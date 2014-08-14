package uk.gov.tna.dri.preingest.loader.unit.network

/**
 * Replacement for Path, which works only on local file systems for remote hosts
 * @param uniqueName concatenation of username + timestamp _ filename
 * @param userName   owner of file
 * @param fileName   name of file minus extension
 * @param fileSize   size of file in bytes
 * @param lastModified  timestamp of file
 * @param path       path to file starting from search root
 */
//class RemotePath(val name:String, val size: Long, val lastModified: Long ) {}

class RemotePath(val uniqueName: String, val userName: String, val fileName: String, val fileSize: Long, val lastModified: Long, val path: String) {}
