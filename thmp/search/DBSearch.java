package thmp.search;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import thmp.utils.DBUtils;
import thmp.utils.DBUtils.AuthorName;
/**
 * Search that relies on database, such as search by authors, within a 
 * given timeframe, etc.
 *
 * @author yihed
 */
public class DBSearch {

	private static final Logger logger = LogManager.getLogger(DBSearch.class);
	
	//should have table name etc in config file!
	public static final String AUTHOR_TABLE_NAME = "authorTb";
	public static final String THM_ID_COL = "thmId";
	public static final String FIRST_NAME_COL = "firstName";
	public static final String MIDDLE_NAME_COL = "middleName";
	public static final String LAST_NAME_COL = "lastName";
	
	public static final String PAPER_ID_COL = "paperId";
	
	public static final String AUTHOR_CSV_FILE = Searcher.SearchMetaData.nameCSVDataPath();
	
	/**
	 * Return list of thms satisfying authorship criteria, by searching 
	 * the database.
	 * Database rows have form "'1710.01688','Benjamin','L','Recht'"
	 * @param authorList
	 * @param conjDisjType
	 * @return List of indices of thms, *not* their ID's!
	 * @throws SQLException 
	 */
	public static List<Integer> searchByAuthor(List<AuthorName> authorList, DBUtils.ConjDisjType conjDisjType) throws SQLException {
		//should pass in first name last name instead!
		
		if(authorList.size() == 0) {
			throw new IllegalArgumentException("List of authors cannot be empty!");
		}
		
		List<Integer> dbList = new ArrayList<Integer>();
		
		String relationStr = conjDisjType.getDbName();
		//data need to be normalized, e.g. M. L. Mangano
		
		//e.g. "SELECT thmId FROM authorTb3 WHERE (author='W. N. Kang' OR author='S. Ivanov');"
		//make DB call, get default connection
		Connection conn = DBUtils.getDefaultDSConnection();
		StringBuilder querySb = new StringBuilder("SELECT ").append(THM_ID_COL).append(" FROM " + AUTHOR_TABLE_NAME 
				+ " WHERE (");
		//decide between first and last author names
		
		//first try all of first name, if no result, try initial 
		//make separate db calls for each author
		
		//now assume only last name.
		for(AuthorName author : authorList) {
			String lastName = author.lastName();
			querySb.append(" ").append(LAST_NAME_COL).append("='").append(lastName).append("' ").append(relationStr);
			
			//for first or middle initial: SELECT thmId FROM authorTb WHERE SUBSTR(firstName,1,1)="z";
			
		}
		querySb.delete(querySb.length() - relationStr.length(), querySb.length());
		querySb.append(");");
		logger.info("searching author - query created: "+querySb);
		
		PreparedStatement stm = conn.prepareStatement(querySb.toString());
		ResultSet rs = stm.executeQuery();
		//System.out.println("rs: "+rs);
		while(rs.next()) {
			int thmId = rs.getInt(1);
			//thmId col first column
			//System.out.println("thmId "+thmId);
			dbList.add(thmId);
		}
		
		return dbList;
	}
	
	
}
