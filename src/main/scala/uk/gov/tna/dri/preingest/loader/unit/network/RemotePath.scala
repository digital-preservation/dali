/**
 * Copyright (c) 2013, The National Archives <digitalpreservation@nationalarchives.gov.uk>
 * http://www.nationalarchives.gov.uk
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package uk.gov.nationalarchives.dri.preingest.loader.unit.network

/**
 * Replacement for Path, which works only on local file systems for remote hosts
 * @param userName   owner of file
 * @param fileName   name of file minus extensions
 * @param fileSize   size of file in bytes
 * @param lastModified  timestamp of file
 * @param path       path to file starting from search root
 */
//class RemotePath(val name:String, val size: Long, val lastModified: Long ) {}

class RemotePath(val userName: String, val fileName: String, val fileSize: Long, val lastModified: Long, val path: String) {}
