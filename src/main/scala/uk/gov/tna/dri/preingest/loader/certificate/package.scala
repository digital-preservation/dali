package uk.gov.tna.dri.preingest.loader

package object certificate {
  type CertificateName = String
  type CertificateData = Array[Byte]

  type CertificateDetail = (CertificateName, CertificateData)
}
