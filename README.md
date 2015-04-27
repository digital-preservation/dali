# DALI - Data Autoloading Infrastructure

A fully asynchronous system for loading and decrypting Units presented to an archive for preservation and/or accession.

This software is in transition to open source, and currently still contains dependencies on National Archive libraries
not generally available. Do not expect it to compile yet.

Currently Supports:

1. TrueCrypted NTFS partitions

2. LUKS-encrypted partitions

3. GNUPG Zipped files

4. Unencrypted file systems

Technologies
------------
* Scala 2.10
* Akka
* Scalatra
* DBus and UDisks
* Unboundid LDAP
* Atomosphere Web Sockets
* Twitter Bootstrap 3.0
* Angular.js
* jQuery
