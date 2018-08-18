

# Semantic Math Parse

## Build 
To build, run the default target:
`ant run`

## Examples


# Theorem Search


## Data pre-processing
To ensure that the search has fast runtime, the arXiv papers are pre-processed to extract and annotate the key statements in each paper. These annotations include various indexing annotations.

To run the data pre-processing code, one needs to have the requisite arXiv data tar files, e.g.   #####
Write the paths of such data files into a metadata file, such as a txt file, so the resulting metadata file contains lines such as: ` `.

The preprocessing has two stages:

1) Extracts each tar file, and indexes the data therein.
2) Takes the results of the first stage for all tar files, and combine 

To run the first processing stage, one can 


## DB Deployment:
If the theorem data are recompiled, i.e. theorem indices are affected in any way, need to deploy
* Author info, e.g. names.
* Related theorems.
* Literal search theorem indices.

* Run WL script function getDataString[] to produce metaDataString1.txt.
* Also run WL script function getNameDBString[] to generate metaDataStringNameDB.txt, which contains lines of form '1710.01696','Tim','','Lemke'. This is used by ProcessMetadataScrape.java to produce paperMetaDataMap.dat. ProjectionMatrix.java then uses this to generate metaDataNameDB.csv.

## Build 
To build, run the default target:
`ant run`

## Examples
The example is 

### 

