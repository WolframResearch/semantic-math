package com.wolfram.puremath.dbapp;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.wolfram.puremath.dbapp.DBUtils.LiteralSearchTb;
import com.wolfram.puremath.dbapp.DBUtils.ThmConceptsTb;

import thmp.search.Searcher;
import thmp.utils.FileUtils;

/**
 * DB utilities class for literal search. Used for encoding
 * literal search indices as bytes.
 * Analogous to ConceptSearchUtils.
 * 
 * @author yihed
 */
public class LiteralSearchUtils {

	private static final int numBitsPerThmIndex = SimilarThmUtils.numBitsPerThmIndex();
	
	/**
	 * Retrieve literal search indices from db to show on web FE.
	 * Use at app runtime.
	 * @param word, In normalized form.
	 * @param conn
	 * @return
	 * @throws SQLException
	 */
	public static List<Integer> getLiteralSearchThmsFromDB(String word,  Connection conn) throws SQLException{
			StringBuilder sb = new StringBuilder(50);
			sb.append("SELECT ").append(LiteralSearchTb.THM_INDICES_COL)
			.append(" FROM ").append(LiteralSearchTb.TB_NAME)
			.append(" WHERE ").append(LiteralSearchTb.WORD_COL)
			.append("=").append(word).append(";");
			
			return SimilarThmUtils.queryByteArray(conn, sb, numBitsPerThmIndex);
	}
	
	/**
	 * Need 3 columns!!!
	 * 
	 * Deploy to the literal search db table.
	 * Use before app deployment.
	 * @param conn
	 * @throws SQLException
	 */
	public static void populateLiteralSearchTb(Connection conn) throws SQLException {
		
		/*If creating table, should be created as CREATE TABLE thmConceptsTb (thmIndex MEDIUMINT(7) UNSIGNED, thmConcepts VARBINARY(39));
		 * 20*15/8 = 37.5*/
		//accomodate changes in number of indices and number of concepts to display!!!
		
		int varbinaryLen = 1;//Searcher.SearchMetaData.maxThmsPerLiteralWord * numBitsPerThmIndex / DBUtils.NUM_BITS_PER_BYTE;
		
		//1 for rounding, 1 extra
		final int padding = 2;
		varbinaryLen += padding;
		
		PreparedStatement pstm;
		
		pstm = conn.prepareStatement("TRUNCATE " + LiteralSearchTb.TB_NAME + ";");		
		pstm.executeUpdate();
		
		//need to pudate table MEDIUMINT(9) UNSIGNED;  VARBINARY(265)
		
		//"ALTER TABLE <table_name> MODIFY <col_name> VARCHAR(65);";	
		pstm = conn.prepareStatement("ALTER TABLE " + LiteralSearchTb.TB_NAME + " MODIFY " 
				+ LiteralSearchTb.THM_INDICES_COL + " VARBINARY(" + varbinaryLen + ");");
		pstm.executeUpdate();
		
		pstm = conn.prepareStatement("ALTER TABLE " + LiteralSearchTb.TB_NAME + " DROP PRIMARY KEY;");
		pstm.executeUpdate();
		
		//Need LiteralSearchIndex!!
		
		//populate table from serialized similar thms indices
		@SuppressWarnings("unchecked")
		List<Map<String, byte[]>> literalSearchMapList 
			= (List<Map<String, byte[]>>)FileUtils.deserializeListFromFile(DBUtils.LiteralSearchTb.literalSearchByteArrayPath);
		
		Map<String, byte[]> literalIndicesMap = literalSearchMapList.get(0);
		
		StringBuilder sb = new StringBuilder(50);
		
		sb.append("ALTER TABLE ").append(LiteralSearchTb.TB_NAME)
		.append(" ADD PRIMARY KEY (")
		.append(LiteralSearchTb.WORD_COL).append(");");
		
		pstm = conn.prepareStatement(sb.toString());
		pstm.executeUpdate();
		sb = new StringBuilder(50);
		sb.append("INSERT INTO " + LiteralSearchTb.TB_NAME + " (")
		.append(LiteralSearchTb.WORD_COL)
		.append(",").append(LiteralSearchTb.THM_INDICES_COL)
		.append(") VALUES(?, ?);");
		
		pstm = conn.prepareStatement(sb.toString());
		
		Set<Map.Entry<String, byte[]>> entrySet = literalIndicesMap.entrySet();
		Iterator<Map.Entry<String, byte[]>> iter = entrySet.iterator();
		int entrySetSz = entrySet.size();
		final int batchSz = 30000;
		System.out.println("Total number of batches: " + Math.ceil(entrySetSz/(double)batchSz));
		
		int counter = 0;
		while(iter.hasNext()) {		
			System.out.println("About to insert batch " + counter++);
			for(int i = 0; i < batchSz && iter.hasNext(); i++) {
				Map.Entry<String, byte[]> entry = iter.next();
				String word = entry.getKey();
				byte[] literalSearchBytes = entry.getValue();
				pstm.setString(1, word);
				pstm.setBytes(2, literalSearchBytes);
				pstm.addBatch();
			}
			pstm.executeBatch();			
		}
	}
	
	public static void main(String[] args) throws SQLException {
		
		Connection conn = DBUtils.getLocalConnection();
		
		populateLiteralSearchTb(conn);
		
	}
	
}
