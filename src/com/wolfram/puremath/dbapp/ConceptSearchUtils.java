package com.wolfram.puremath.dbapp;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.wolfram.puremath.dbapp.DBUtils.ThmConceptsTb;

import thmp.search.Searcher;
import thmp.utils.FileUtils;

/**
 * Utilities used for deploying to db and retrieving thms that contain those
 * exact words.
 * 
 * @author yihed
 */
public class ConceptSearchUtils {
	
	private static final int bitsPerWordIndex = thmp.search.ConceptSearch.NUM_BITS_PER_WORD_INDEX();
	
	//retrieve words from db to show on web FE
	public static List<Integer> getThmConceptsFromDB(int thmIndex,  Connection conn) throws SQLException{
		StringBuilder sb = new StringBuilder(50);
		sb.append("SELECT ").append(ThmConceptsTb.CONCEPTS_COL)
		.append(" FROM ").append(ThmConceptsTb.TB_NAME)
		.append(" WHERE ").append(ThmConceptsTb.INDEX_COL)
		.append("=").append(thmIndex).append(";");
		
		return SimilarThmUtils.queryByteArray(conn, sb, bitsPerWordIndex);		
	}
	
	/**
	 * Deploy to the thm concepts db table.
	 * @param conn
	 * @throws SQLException
	 */
	public static void populateThmConceptsTb(Connection conn) throws SQLException {
		
		/*If creating table, should be created as CREATE TABLE thmConceptsTb (thmIndex MEDIUMINT(7) UNSIGNED, thmConcepts VARBINARY(39));
		 * 20*15/8 = 37.5*/
		//accomodate changes in number of indices and number of concepts to display!!!
		
		int varbinaryLen = Searcher.SearchMetaData.maxConceptsPerThmNum * bitsPerWordIndex / DBUtils.NUM_BITS_PER_BYTE;
		
		//1 for rounding, 1 extra
		final int padding = 2;
		varbinaryLen += padding;
		
		PreparedStatement pstm;
		
		pstm = conn.prepareStatement("TRUNCATE " + ThmConceptsTb.TB_NAME + ";");		
		pstm.executeUpdate();
		
		//need to pudate table MEDIUMINT(9) UNSIGNED;  VARBINARY(265)
		
		//"ALTER TABLE <table_name> MODIFY <col_name> VARCHAR(65);";		
		pstm = conn.prepareStatement("ALTER TABLE " + ThmConceptsTb.TB_NAME + " MODIFY " 
				+ ThmConceptsTb.CONCEPTS_COL + " VARBINARY(" + varbinaryLen + ");");
		pstm.executeUpdate();
		
		pstm = conn.prepareStatement("ALTER TABLE " + ThmConceptsTb.TB_NAME + " DROP PRIMARY KEY;");
		pstm.executeUpdate();
		
		//populate table from serialized similar thms indices
		@SuppressWarnings("unchecked")
		List<Map<Integer, byte[]>> thmConceptsMapList 
			= (List<Map<Integer, byte[]>>)FileUtils.deserializeListFromFile(DBUtils.ThmConceptsTb.thmConceptsByteArrayPath);
		
		Map<Integer, byte[]> thmConceptsMap = thmConceptsMapList.get(0);
		
		StringBuilder sb = new StringBuilder(50);
		
		sb.append("ALTER TABLE ").append(ThmConceptsTb.TB_NAME)
		.append(" ADD PRIMARY KEY (")
		.append(ThmConceptsTb.INDEX_COL).append(");");
		
		pstm = conn.prepareStatement(sb.toString());
		pstm.executeUpdate();
		sb = new StringBuilder(50);
		sb.append("INSERT INTO " + ThmConceptsTb.TB_NAME + " (")
		.append(ThmConceptsTb.INDEX_COL)
		.append(",").append(ThmConceptsTb.CONCEPTS_COL)
		.append(") VALUES(?, ?);");
		
		pstm = conn.prepareStatement(sb.toString());
		
		Set<Map.Entry<Integer, byte[]>> entrySet = thmConceptsMap.entrySet();
		Iterator<Map.Entry<Integer, byte[]>> iter = entrySet.iterator();
		int entrySetSz = entrySet.size();
		final int batchSz = 30000;
		System.out.println("Total number of batches: " + Math.ceil(entrySetSz/(double)batchSz));
		
		int counter = 0;
		while(iter.hasNext()) {		
			System.out.println("About to insert batch " + counter++);
			for(int i = 0; i < batchSz && iter.hasNext(); i++) {
				Map.Entry<Integer, byte[]> entry = iter.next();
				int thmIndex = entry.getKey();
				byte[] thmConceptsBytes = entry.getValue();
				pstm.setInt(1, thmIndex);
				pstm.setBytes(2, thmConceptsBytes);
				pstm.addBatch();
			}
			pstm.executeBatch();			
		}
	}
	
	public static void main(String[] args) throws SQLException {
		
		Connection conn = DBUtils.getLocalConnection();
		
		populateThmConceptsTb(conn);
		
	}
}
