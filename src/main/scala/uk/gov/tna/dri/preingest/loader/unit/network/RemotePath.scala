package uk.gov.tna.dri.preingest.loader.unit.network

/**
 * Replacement for Path, which works only on local file systems for remote hosts
 * @param name
 * @param size
 * @param lastModified
 */
class RemotePath(val name:String, val size: Long, val lastModified: Long ) {}


