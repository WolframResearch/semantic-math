

##Parse


##Search


##DB Deployment:
If the theorem data are recompiled, i.e. theorem indices are affected in any way, need to deploy
* Author info, e.g. names.
* Related theorems.
* Literal search theorem indices.

* Run WL script function getDataString[] to produce metaDataString1.txt.
* Also run WL script function getNameDBString[] to generate metaDataStringNameDB.txt, which contains lines of form '1710.01696','Tim','','Lemke'. This is used by ProcessMetadataScrape.java to produce paperMetaDataMap.dat. ProjectionMatrix.java then uses this to generate metaDataNameDB.csv.

##Build

Run with default target:
ant run
