# --- Object Storage Bucket for PostgreSQL Backups ---

resource "oci_objectstorage_bucket" "postgres_backups" {
  compartment_id        = var.compartment_ocid
  name                  = "oke-postgres-backups-${var.cluster_name}"
  namespace             = data.oci_objectstorage_namespace.this.namespace
  access_type           = "NoPublicAccess"
  storage_tier          = "Standard"
  object_events_enabled = false
  versioning            = "Enabled"

  lifecycle {
    prevent_destroy = true
  }
}
