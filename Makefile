TOFU := tofu
NAMESPACE ?= postgres

.PHONY: init plan apply destroy fmt validate output console
.PHONY: deploy-postgres destroy-postgres reset-postgres install-barman-plugin install-operator

init:
	$(TOFU) init

plan:
	$(TOFU) plan -out=tfplan

apply:
	$(TOFU) apply tfplan

destroy:
	$(TOFU) destroy

fmt:
	$(TOFU) fmt

validate:
	$(TOFU) validate

output:
	$(TOFU) output

console:
	@echo "Run: oci ce cluster create-kubeconfig --cluster-id $$($(TOFU) output -raw cluster_id) --file ~/.kube/config --region us-ashburn-1 --token-version 2.0.0"
	@echo "Then: kubectl get nodes"

deploy-postgres:
	@if kubectl get namespace cnpg-system &>/dev/null 2>&1; then \
		echo "Cleaning up residual state from prior run..."; \
		scripts/cleanup-cnpg.sh $(NAMESPACE) 2>/dev/null || true; \
		scripts/wait-for-namespace-gone.sh cnpg-system 120; \
	fi
	kubectl create namespace $(NAMESPACE) --dry-run=client -o yaml | kubectl apply -f -
	helm upgrade --install cnpg cnpg/cloudnative-pg \
		--namespace cnpg-system --create-namespace --wait --timeout 5m
	@echo "Installing OCI Block Volume CSI Driver..."
	oci ce cluster addon install --addon-name oci-bv-csi-driver \
		--cluster-id $$(tofu output -raw cluster_id) \
		--is-permanently-enabled true \
		--wait-for-work-request 2>/dev/null || \
	oci ce cluster addon update --addon-name oci-bv-csi-driver \
		--cluster-id $$(tofu output -raw cluster_id) \
		--is-permanently-enabled true \
		--wait-for-work-request 2>/dev/null
	kubectl get storageclass oci-bv
	kubectl apply -f k8s/postgres/cluster.yaml -n $(NAMESPACE)
	kubectl wait --for=condition=Ready pod -l postgresql=postgres-cluster \
		-n $(NAMESPACE) --timeout=300s
	./scripts/setup-backup.sh $(NAMESPACE)

destroy-postgres:
	scripts/cleanup-cnpg.sh $(NAMESPACE) 2>/dev/null || true

reset-postgres: destroy-postgres deploy-postgres

install-barman-plugin:
	@if kubectl get namespace cnpg-system &>/dev/null 2>&1; then \
		echo "Cleaning up residual state from prior run..."; \
		scripts/cleanup-cnpg.sh $(NAMESPACE) 2>/dev/null || true; \
		scripts/wait-for-namespace-gone.sh cnpg-system 120; \
	fi
	@echo "Cleaning up orphaned cert-manager cluster-scoped resources..."
	@kubectl get clusterrole -o name 2>/dev/null | grep -E '^clusterrole\.rbac\.authorization\.k8s\.io/cert-manager-' | xargs -r kubectl delete --ignore-not-found 2>/dev/null || true
	@kubectl get clusterrolebinding -o name 2>/dev/null | grep -E '^clusterrolebinding\.rbac\.authorization\.k8s\.io/cert-manager-' | xargs -r kubectl delete --ignore-not-found 2>/dev/null || true
	@kubectl get role --all-namespaces --no-headers 2>/dev/null | awk '$2 ~ /^cert-manager/ {print $1, $2}' | while read ns name; do kubectl delete role -n "$$ns" "$$name" --ignore-not-found 2>/dev/null || true; done
	@kubectl get rolebinding --all-namespaces --no-headers 2>/dev/null | awk '$2 ~ /^cert-manager/ {print $1, $2}' | while read ns name; do kubectl delete rolebinding -n "$$ns" "$$name" --ignore-not-found 2>/dev/null || true; done
	helm repo add jetstack https://charts.jetstack.io 2>/dev/null || true
	helm repo update
	helm upgrade --install cert-manager jetstack/cert-manager \
		--namespace cert-manager --create-namespace \
		--set crds.enabled=true --wait --timeout 3m
	kubectl create namespace cnpg-system --dry-run=client -o yaml | kubectl apply -f -
	kubectl apply -f https://github.com/cloudnative-pg/plugin-barman-cloud/releases/download/v0.12.0/manifest.yaml
	kubectl rollout status deployment -n cnpg-system barman-cloud --timeout=120s

install-operator: install-barman-plugin
	helm repo add cnpg https://cloudnative-pg.github.io/charts 2>/dev/null || true
	helm repo update
	helm upgrade --install cnpg cnpg/cloudnative-pg \
		--namespace cnpg-system --create-namespace --wait --timeout 5m
