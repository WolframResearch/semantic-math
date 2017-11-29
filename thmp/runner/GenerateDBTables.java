package thmp.runner;

import java.util.List;
import java.util.Map;

import javax.sql.DataSource;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;

import thmp.runner.ProcessMetadataScrape.PaperMetaData;
import thmp.utils.DBUtils;
import thmp.utils.FileUtils;

/**
 * Generate database tables.
 * @author yihed
 *
 */
public class GenerateDBTables {	
	
	public static void main(String[] args) throws SQLException {
		@SuppressWarnings("unchecked")
		List<Map<String, ProcessMetadataScrape.PaperMetaData>> idMapList 
			= (List<Map<String, ProcessMetadataScrape.PaperMetaData>>)FileUtils
			.deserializeListFromFile(//FileUtils.getPathIfOnServlet(ProcessMetadataScrape.paperMetaDataMapSerFileStr())
					"src/thmp/data/paperMetaDataMapSample.dat"
					);
		
		//keys are //paper id, e.g. 1234.5678, and values contain info on title, author, etc.
		Map<String, PaperMetaData> idPaperMetaDataMap = idMapList.get(0);
		DataSource ds = com.wolfram.puremath.dbapp.DBUtils.getLocalDataSource(DBUtils.DEFAULT_DB_NAME, DBUtils.DEFAULT_USER, DBUtils.DEFAULT_PW, 
				DBUtils.DEFAULT_SERVER, DBUtils.DEFAULT_PORT);
		Connection conn = ds.getConnection();
		
		//"CREATE TABLE authorTb (thmId INT(20),"
		//"CREATE TABLE a (thmId INT(20) author VARCHAR(20), content )"
		int counter = 0;
		//insert these into database
		for(Map.Entry<String, PaperMetaData> entry : idPaperMetaDataMap.entrySet()) {
			if(++counter > 200) {
				break;
			}
			PaperMetaData paperMetaData = entry.getValue();
			
			String paperId = entry.getKey();
			
			com.wolfram.puremath.dbapp.DBUtils.manipulateData("INSERT INTO paperTb (thmId, title) VALUES ('"+paperId
					+"','" + paperMetaData.title()+"')", conn);
			
			String authorsStr = paperMetaData.authors();
			
			//should have different authors as separate strings!
			String[] authorsStrAr = authorsStr.split(", ");
			//paperTb, authorTb3
			for(String author : authorsStrAr) {
				com.wolfram.puremath.dbapp.DBUtils.manipulateData("INSERT INTO authorTb3 (thmId, author) VALUES ('"+paperId+"','"+author+"')", conn);
				
			}
			
		}
		
		
	}
	
}
