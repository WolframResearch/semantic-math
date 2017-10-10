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
/**
 * Search that relies on database, such as search by authors, within a 
 * given timeframe, etc.
 *
 * @author yihed
 */
public class DBSearch {

	private static final Logger logger = LogManager.getLogger(DBSearch.class);
	
	//should have table name etc in config file!
	private static final String AUTHOR_TABLE_NAME = "authorTb3";
	private static final String THM_ID_NAME = "thmId";
	
	/**
	 * Return list of thms satisfying authorship criteria, by searching 
	 * the database.
	 * @param authorList
	 * @param conjDisjType
	 * @return List of indices of thms, *not* their ID's!
	 * @throws SQLException 
	 */
	public static List<Integer> searchByAuthor(String[] authorAr, DBUtils.ConjDisjType conjDisjType) throws SQLException {
		
		if(authorAr.length == 0) {
			throw new IllegalArgumentException("Array of authors cannot be empty!");
		}
		
		List<Integer> dbList = new ArrayList<Integer>();
		
		String relationStr = conjDisjType.getDbName();
		//e.g. "SELECT thmId FROM authorTb3 WHERE (author='W. N. Kang' OR author='S. Ivanov');"
		//make DB call, get default connection
		Connection conn = DBUtils.getDefaultDSConnection();
		StringBuilder querySb = new StringBuilder("SELECT ").append(THM_ID_NAME).append(" FROM " + AUTHOR_TABLE_NAME 
				+ " WHERE (");
		
		//now assume only last name.
		for(String author : authorAr) {
			querySb.append(" author='").append(author).append("' ").append(relationStr);
		}
		querySb.delete(querySb.length() - relationStr.length(), querySb.length() - 1);
		querySb.append("');");
		
		PreparedStatement stm = conn.prepareStatement(querySb.toString());
		ResultSet rs = stm.executeQuery();
		System.out.println("rs: "+rs);
		while(rs.next()) {
			int thmId = rs.getInt(1);
			//thmId col first column
			System.out.println("thmId "+thmId);
			dbList.add(thmId);
		}
		
		return dbList;
	}
	
	
}
