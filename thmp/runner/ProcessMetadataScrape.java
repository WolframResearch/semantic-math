package thmp.runner;

import java.io.Serializable;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import thmp.utils.FileUtils;

/**
 * Process arXiv metadata scrape, create and serialize resulting map. 
 * Metadata such as author, date, etc. 
 * @author yihed
 *
 */
public class ProcessMetadataScrape {
	
	public static final String paperMetaDataMapSerFileStr = "src/thmp/data/paperMetaDataMap.dat";
	
	//"id", "created", "title", "Authors"
	public static class PaperMetaData implements Serializable{

		private static final long serialVersionUID = -8845225732288864837L;
		//number of metadata elements per paper.
		private static final int METADATA_NUM = 4;
		//String paperId;
		private String date;
		private String title;
		private String authors;
		
		PaperMetaData(/*String paperId_,*/ String date_, String title_, String authors_){
			//this.paperId = paperId_;
			this.date = date_;
			this.title = title_;
			this.authors = authors_;
		}

		public String date() {
			return date;
		}
		public String title() {
			return title;
		}
		public String authors() {
			return authors;
		}
	}
	
	public static void main(String[] args){
		final String fileStr = "src/thmp/data/metaDataString.txt";
		//avoid accidentally running this class.
		boolean buildMap = false;
		if(buildMap){
			Map<String, PaperMetaData> metaDataMap = processMetadataScrape(fileStr);
			List<Map<String,PaperMetaData>> metaDataList = new ArrayList<Map<String,PaperMetaData>>();
			metaDataList.add(metaDataMap);
			FileUtils.serializeObjToFile(metaDataList, paperMetaDataMapSerFileStr);
		}
	}

	private static Map<String, PaperMetaData> processMetadataScrape(String fileStr) {
		
		List<String> fileNameList = new ArrayList<String>();
		fileNameList.add(fileStr);
		List<String> metaDataLines = FileUtils.readLinesFromFiles(fileNameList, Charset.forName("UTF-8"));
		Map<String, PaperMetaData> metaDataMap = new HashMap<String, PaperMetaData>();
		int totalLineCount = metaDataLines.size();
		System.out.println("totalLineCount "+totalLineCount);
		int num4tuples = totalLineCount/PaperMetaData.METADATA_NUM;
		
		for(int i = 0; i < num4tuples; i++){
			int curIndex = PaperMetaData.METADATA_NUM * i;
			String paperId = metaDataLines.get(curIndex);
			String date = metaDataLines.get(curIndex+1);
			String title = metaDataLines.get(curIndex+2);
			String authors = metaDataLines.get(curIndex+3);
			int authorsLen = authors.length();
			if(authors.charAt(authorsLen-2) == ','){
				authors = authors.substring(0, authorsLen-2);
			}			
			metaDataMap.put(paperId, new PaperMetaData(date, title, authors));			
		}
		return metaDataMap;
	}
	
}
