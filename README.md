
Database deployment and query scripts

Tables:

The database includes several tables:

1) AuthorTb
2) 
3) 
createAuthorTb(conn);
		AuthorUtils.populateAuthorTb(conn, dirpath);
		
		ThmHypUtils.createThmHypTb(conn);
		ThmHypUtils.populateThmHypTb

## DB Deployment:

If the theorem data are recompiled, i.e. the second stage above is rerun, hence affecting theorem indexing, the database needs to be redeployed with the update data. The data include:

* Author info, e.g. names.
* Related theorems.
* Literal search theorem indices.

* Run WL script function getDataString[] to produce metaDataString1.txt.
* Also run WL script function getNameDBString[] to generate metaDataStringNameDB.txt, which contains lines of form '1710.01696','Tim','','Lemke'. This is used by ProcessMetadataScrape.java to produce paperMetaDataMap.dat. ProjectionMatrix.java then uses this to generate metaDataNameDB.csv.


On the server machine hosting the databases, deployment can be run as follows:

```bash java -cp dbApp.jar:log4j-core-2.6.2.jar:log4j-api-2.6.2.jar:mysql-jdbc-5.1.44.jar:thmProj.jar com.wolfram.puremath.dbapp.DBDeploy```