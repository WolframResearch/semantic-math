package thmp.runner;

import java.io.Serializable;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import thmp.utils.FileUtils;

/**
 * Process arXiv metadata scrape, create and serialize resulting map. 
 * Metadata such as author, date, etc. 
 * @author yihed
 *
 */
public class ProcessMetadataScrape {
	
	private static final String paperMetaDataMapSerFileStr = "src/thmp/data/paperMetaDataMap.dat";
	private static final String paperMetaDataMapTxtFileStr = "src/thmp/data/paperMetaDataMap.txt";
	//e.g. "quant-ph/9905093"
	private static final Pattern PAPER_ID_PATT = Pattern.compile("(.+?)/([\\d.]+)");
	
	//"id", "created", "title", "Authors"
	public static class PaperMetaData implements Serializable{

		private static final long serialVersionUID = -8845225732288864837L;
		//number of metadata elements per paper.
		//paperId, date title authors
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
	
	public static String paperMetaDataMapSerFileStr() {
		return paperMetaDataMapSerFileStr;
	}
	
	public static void main(String[] args){
		
		buildMap();
	}

	private static void buildMap() {
		//source file containing metadata 
		final String fileStr = "src/thmp/data/metaDataString1.txt";
				//avoid accidentally running this class.
		boolean buildMap = true;
		if(buildMap){
					Map<String, PaperMetaData> metaDataMap = processMetadataScrape(fileStr);
					List<Map<String, PaperMetaData>> metaDataList = new ArrayList<Map<String,PaperMetaData>>();
					metaDataList.add(metaDataMap);
					FileUtils.serializeObjToFile(metaDataList, paperMetaDataMapSerFileStr);
					//for human inspection purposes
					FileUtils.writeToFile(metaDataMap, paperMetaDataMapTxtFileStr);
		}
	}
	
	private static Map<String, PaperMetaData> processMetadataScrape(String fileStr) {
		
		List<String> fileNameList = new ArrayList<String>();
		fileNameList.add(fileStr);
		List<String> metaDataLines = FileUtils.readLinesFromFiles(fileNameList, Charset.forName("UTF-8"));
		Map<String, PaperMetaData> metaDataMap = new HashMap<String, PaperMetaData>();
		int totalLineCount = metaDataLines.size();
		System.out.println("totalLineCount "+totalLineCount);
		int num5tuples = totalLineCount/PaperMetaData.METADATA_NUM;
		Matcher m;
		
		for(int i = 0; i < num5tuples; i++){
			int curIndex = PaperMetaData.METADATA_NUM * i;
			String paperId = metaDataLines.get(curIndex);
			//if paperId has form "math-ph/9905093", combine first + second parts.
			//only math and math-ph papers were selected for file.
			if((m=PAPER_ID_PATT.matcher(paperId)).matches()) {
				paperId = m.group(1)+m.group(2);				
			}
			
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
	
	private static void extractSample() {
		final String sampleSerStr = "src/thmp/data/paperMetaDataMapSample.dat";
		Map<String, ProcessMetadataScrape.PaperMetaData> mapSample 
			= new HashMap<String, ProcessMetadataScrape.PaperMetaData>();
		
		@SuppressWarnings("unchecked")
		List<Map<String, ProcessMetadataScrape.PaperMetaData>> idMapList 
			= (List<Map<String, ProcessMetadataScrape.PaperMetaData>>)FileUtils
			.deserializeListFromFile(FileUtils.getPathIfOnServlet(ProcessMetadataScrape.paperMetaDataMapSerFileStr()));
		//keys are //paper id, e.g. 1234.5678, and values contain info on title, author, etc.
		Map<String, PaperMetaData> idPaperMetaDataMap = idMapList.get(0);
		int counter = 0;
		for(Map.Entry<String, PaperMetaData> entry : idPaperMetaDataMap.entrySet()) {
			if(++counter > 200) {
				break;
			}
			mapSample.put(entry.getKey(),entry.getValue());
		}
		
		List<Map<String, ProcessMetadataScrape.PaperMetaData>> sampleMapList 
			= new ArrayList<Map<String, ProcessMetadataScrape.PaperMetaData>>();
		sampleMapList.add(mapSample);
		FileUtils.serializeObjToFile(sampleMapList, sampleSerStr);
	}
	
}
