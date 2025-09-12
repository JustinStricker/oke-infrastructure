# OKE Cluster Deployment with Terraform and GitHub Actions

This repository contains Terraform code to provision an Oracle Kubernetes Engine (OKE) cluster on Oracle Cloud Infrastructure (OCI). It is designed to be fully automated using a CI/CD pipeline with GitHub Actions.

## Overview

The Terraform configuration will create the following resources:
* A Virtual Cloud Network (VCN) with public subnets.
* An Internet Gateway and associated Route Table for public access.
* Three subnets for the Kubernetes API endpoint, worker nodes, and load balancers.
* Detailed Security Lists to control traffic between subnets and the internet.
* An OKE Cluster (Control Plane).
* A Node Pool with compute instances (Worker Nodes) distributed across availability domains.

The GitHub Actions workflow automates the deployment process, running `terraform plan` on pull requests and `terraform apply` on pushes to the `main` branch.

## Prerequisites

Before you begin, you will need the following:
1.  An Oracle Cloud Infrastructure (OCI) account.
2.  An OCI user with sufficient permissions to create the resources defined in `main.tf`.
3.  An OCI API key pair for that user. You will need the private key content, the key's fingerprint, your user OCID, and your tenancy OCID.
4.  A GitHub repository for this code.

## Setup for Automation

To enable the GitHub Actions workflow, you must store your OCI credentials as encrypted secrets in your repository.

1.  Navigate to your GitHub repository's **Settings > Secrets and variables > Actions**.
2.  Click **New repository secret** for each of the secrets listed below.

### Required GitHub Secrets

* `OCI_COMPARTMENT_OCID`: The OCID of the compartment where the OKE cluster will be created.
* `OCI_TENANCY_OCID`: Your OCI tenancy's OCID.
* `OCI_USER_OCID`: The OCID of the user for API authentication.
* `OCI_FINGERPRINT`: The fingerprint of your API public key.
* `OCI_PRIVATE_KEY`: The **full content** of your PEM-formatted API private key file.
* `OCI_REGION`: The OCI region you are deploying to (e.g., `us-ashburn-1`).

## How It Works

The CI/CD pipeline is defined in `.github/workflows/main.yml`.

* **On Pull Request:** When a pull request is opened targeting the `main` branch, the workflow will run `terraform init` and `terraform plan`. This provides a preview of the infrastructure changes, which you can review directly in the pull request's "Checks" tab. No changes are applied at this stage.
* **On Push to `main`:** After a pull request is merged, the workflow runs again on the `main` branch. This time, it will execute `terraform apply -auto-approve`, which provisions or updates the infrastructure on OCI.

## Connecting to Your Cluster

Once the `terraform apply` job has completed successfully, you can configure `kubectl` to connect to your new OKE cluster.

1.  **Install the OCI CLI:** Follow the [official instructions](https://docs.oracle.com/en-us/iaas/Content/API/SDKDocs/cliinstall.htm) to install and configure the OCI CLI.
2.  **Get Cluster OCID:** Find the OCID of your cluster from the OCI console or from the Terraform output.
3.  **Generate Kubeconfig:** Run the following OCI CLI command to generate the `kubeconfig` file needed to access your cluster:

    ```sh
    oci ce cluster create-kubeconfig --cluster-id <your_cluster_ocid> --file $HOME/.kube/config --region <your_region> --token-version 2.0.0
    ```

4.  **Verify Connection:** Test your connection to the cluster:
    ```sh
    kubectl get nodes
    ```

## Tearing Down the Infrastructure

To destroy all the resources created by this Terraform configuration, you can run the following command locally (after configuring your environment with OCI credentials):

```sh
terraform destroy

