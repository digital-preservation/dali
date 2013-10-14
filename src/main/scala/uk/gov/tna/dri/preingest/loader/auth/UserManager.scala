package uk.gov.tna.dri.preingest.loader.auth

trait AuthManager[K, V] {

  def find(key: K): Option[V]
}
