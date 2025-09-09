# Ktor on OKE with Terraform and GitHub Actions.

This repository contains a simple **Ktor** application, along with the necessary **Terraform** configuration and **GitHub Actions** workflow to deploy it to **Oracle Kubernetes Engine (OKE)** using only free-tier eligible resources.

-----

## Project Structure

```
.
├── .github/
│   └── workflows/
│       └── main.yml           # GitHub Actions workflow
├── src/
│   └── main/
│       └── kotlin/
│           └── com/
│               └── example/
│                   └── Application.kt # Ktor application source
├── .gitignore               # Specifies intentionally untracked files to ignore
├── build.gradle.kts           # Gradle build file for the Ktor app
├── Dockerfile                 # Dockerfile to containerize the app
├── main.tf                    # Main Terraform configuration
├── variables.tf               # Terraform variables
└── outputs.tf                 # Terraform outputs
```

-----

## Prerequisites

  * An **Oracle Cloud Infrastructure (OCI)** account.
  * A **GitHub** account.
  * Required OCI secrets configured in your GitHub repository for the Actions workflow:
      * `OCI_TENANCY_OCID`
      * `OCI_USER_OCID`
      * `OCI_FINGERPRINT`
      * `OCI_PRIVATE_KEY`
      * `OCI_REGION`
      * `OCI_COMPARTMENT_OCID`

-----

## How It Works

1.  **Push to `main` branch**: When code is pushed to the `main` branch, the GitHub Actions workflow is triggered.
2.  **Build and Push Docker Image**: The workflow builds a Docker image of the Ktor application and pushes it to the **Oracle Cloud Infrastructure Registry (OCIR)**.
3.  **Run Terraform**: The workflow then uses Terraform to provision the OKE cluster and deploy the application using the Docker image from OCIR.rkflow builds a Docker image of the Ktor application and pushes it to the Oracle Cloud Infrastructure Registry (OCIR).

Run Terraform: The workflow then uses Terraform to provision the OKE cluster and deploy the application using the Docker image from OCIR.
