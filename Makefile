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
	fi
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
