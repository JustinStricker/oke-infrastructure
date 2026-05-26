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
	@if kubectl get namespace cnpg-system -o jsonpath='{.status.phase}' 2>/dev/null | grep -q Terminating; then \
		echo "cnpg-system is Terminating — forcing cleanup..."; \
		kubectl api-resources --verbs=patch --namespaced=true -o name 2>/dev/null | \
			while IFS= read -r r; do \
				kubectl patch "$$r" --all -n cnpg-system \
					-p '{"metadata":{"finalizers":[]}}' --type=merge 2>/dev/null || true; \
			done; \
		kubectl patch namespace cnpg-system \
			-p '{"metadata":{"finalizers":[]}}' --type=merge 2>/dev/null || true; \
		sleep 3; \
		kubectl delete namespace cnpg-system --wait=true 2>/dev/null || true; \
	fi
	kubectl create namespace $(NAMESPACE) --dry-run=client -o yaml | kubectl apply -f -
	helm upgrade --install cnpg cnpg/cloudnative-pg \
		--namespace cnpg-system --create-namespace --wait --timeout 5m
	kubectl apply -f k8s/postgres/cluster.yaml -n $(NAMESPACE)
	kubectl wait --for=condition=Ready pod -l postgresql=postgres-cluster \
		-n $(NAMESPACE) --timeout=300s
	./scripts/setup-backup.sh $(NAMESPACE)

destroy-postgres:
	kubectl delete -f k8s/postgres/cluster.yaml -n $(NAMESPACE) --wait=true 2>/dev/null || true
	kubectl delete objectstore postgres-cluster-backups -n $(NAMESPACE) --wait=true 2>/dev/null || true
	kubectl delete pvc -n $(NAMESPACE) --all --wait=true 2>/dev/null || true

reset-postgres: destroy-postgres deploy-postgres

install-barman-plugin:
	kubectl apply -f https://github.com/cert-manager/cert-manager/releases/download/v1.16.2/cert-manager.yaml
	kubectl wait --for=condition=Available deployment cert-manager -n cert-manager --timeout=120s
	@if kubectl get namespace cnpg-system -o jsonpath='{.status.phase}' 2>/dev/null | grep -q Terminating; then \
		echo "cnpg-system is Terminating — forcing cleanup..."; \
		kubectl api-resources --verbs=patch --namespaced=true -o name 2>/dev/null | \
			while IFS= read -r r; do \
				kubectl patch "$$r" --all -n cnpg-system \
					-p '{"metadata":{"finalizers":[]}}' --type=merge 2>/dev/null || true; \
			done; \
		kubectl patch namespace cnpg-system \
			-p '{"metadata":{"finalizers":[]}}' --type=merge 2>/dev/null || true; \
		sleep 3; \
		kubectl delete namespace cnpg-system --wait=true 2>/dev/null || true; \
	fi
	kubectl create namespace cnpg-system --dry-run=client -o yaml | kubectl apply -f -
	kubectl apply -f https://github.com/cloudnative-pg/plugin-barman-cloud/releases/download/v0.12.0/manifest.yaml
	kubectl rollout status deployment -n cnpg-system barman-cloud --timeout=120s

install-operator: install-barman-plugin
	helm repo add cnpg https://cloudnative-pg.github.io/charts 2>/dev/null || true
	helm repo update
	helm upgrade --install cnpg cnpg/cloudnative-pg \
		--namespace cnpg-system --create-namespace --wait --timeout 5m
