package com.wolfram.puremath.dbapp;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import com.google.common.collect.ListMultimap;
import com.wolfram.puremath.dbapp.DBUtils.LiteralSearchTb;

import thmp.search.LiteralSearch;
import thmp.search.LiteralSearch.LiteralSearchIndex;
import thmp.search.Searcher;
import thmp.utils.FileUtils;

/**
 * DB utilities class for literal search. Used for encoding
 * literal search indices as bytes (rather than having a 600 mb map
 * in memory).
 * Table contains 3 columns, word column, byte array col of thm indices,
 * and col of the row word's index in the thm.
 * Analogous to ConceptSearchUtils.
 * 
 * @author yihed
 */
public class LiteralSearchUtils {

	private static final int numBitsPerThmIndex = SimilarThmUtils.numBitsPerThmIndex();
	/**Number of bits per word index, word index being an element of the word index array for a thm*/
	private static final int numBitsPerWordIndex = LiteralSearchIndex.NUM_BITS_PER_WORD_INDEX;
	
	/**
	 * Retrieve literal search thm indices, and the indices of that word
	 * in these thms, from db.
	 * 
	 * Use at app runtime.
	 * @param word, In normalized form.
	 * @param conn
	 * @return
	 * @throws SQLException
	 */
	public static void getLiteralSearchThmsFromDB(String word,  Connection conn,
			List<Integer> thmIndexList, List<Integer> wordsIndexArList) throws SQLException{
		
		StringBuilder querySb = new StringBuilder(60);
		querySb.append("SELECT ").append(LiteralSearchTb.THM_INDICES_COL)
		.append(", ").append(LiteralSearchTb.WORD_INDICES_COL)
		.append(" FROM ").append(LiteralSearchTb.TB_NAME)
		.append(" WHERE ").append(LiteralSearchTb.WORD_COL)
		.append("=").append(word).append(";");
		
		PreparedStatement pstm = conn.prepareStatement(querySb.toString());
		
		ResultSet rs = pstm.executeQuery();
		byte[] indexBytes;
		byte[] wordsIndexArBytes;
	 	if(rs.next()) {
			indexBytes = rs.getBytes(DBUtils.LiteralSearchTb.THM_INDICES_COL);
			wordsIndexArBytes = rs.getBytes(DBUtils.LiteralSearchTb.WORD_INDICES_COL);
		}else {
			pstm.close();
			return;
		}
		pstm.close();
		rs.close();
		
		thmIndexList.addAll(SimilarThmUtils.byteArrayToIndexList(indexBytes, numBitsPerThmIndex));
		wordsIndexArList.addAll(SimilarThmUtils.byteArrayToIndexList(wordsIndexArBytes, numBitsPerWordIndex));
		
	}
	
	/**
	 * Need 3 columns, word column, thm index,
	 * and col of word indices in thm.
	 * So each word 
	 * Deploy to the literal search db table.
	 * Use before app deployment.
	 * @param conn
	 * @throws SQLException
	 */
	public static void populateLiteralSearchTb(Connection conn) throws SQLException {
		
		/*If creating table, should be created as e.g.
		 * CREATE TABLE literalSearchTb (word VARCHAR(15), thmIndices VARBINARY(789), wordIndices VARBINARY(600))
		 * 20*15/8 = 37.5*/
		//accomodate changes in number of indices and number of concepts to display!!!
		
		int maxThmsPerLiteralWord = Searcher.SearchMetaData.maxThmsPerLiteralWord;
		//number of bytes per list. As of Dec 29, 2017, num bytes for thmIndices is 300*21/8 = 787.5 + 2 ~ 789
		//For wordIndices it is 300 * 2 * 8/8 = 600
		int varbinaryLen = maxThmsPerLiteralWord * numBitsPerThmIndex / DBUtils.NUM_BITS_PER_BYTE;
		
		//1 for rounding, 1 extra
		final int padding = 2;
		varbinaryLen += padding;
		
		PreparedStatement pstm;
		
		pstm = conn.prepareStatement("TRUNCATE " + LiteralSearchTb.TB_NAME + ";");		
		pstm.executeUpdate();
		
		//need to update table MEDIUMINT(9) UNSIGNED;  VARBINARY(265)
		int wordIndexArTotalLen = LiteralSearchIndex.MAX_INDEX_COUNT_PER_WORD; //this value is 2
		int maxWordsArListLen = maxThmsPerLiteralWord * wordIndexArTotalLen;
		
		int wordArVarBinaryLen = maxWordsArListLen * numBitsPerWordIndex / DBUtils.NUM_BITS_PER_BYTE;
		//"ALTER TABLE <table_name> MODIFY <col_name> VARCHAR(65);";
		pstm = conn.prepareStatement("ALTER TABLE " + LiteralSearchTb.TB_NAME 
				+ " MODIFY " + LiteralSearchTb.WORD_COL + " VARCHAR(" + LiteralSearch.LITERAL_WORD_LEN_MAX + "),"
				+ " MODIFY " + LiteralSearchTb.THM_INDICES_COL + " VARBINARY(" + varbinaryLen + "),"
				+ " MODIFY " + LiteralSearchTb.WORD_INDICES_COL + " VARBINARY(" + wordArVarBinaryLen + ");");
		pstm.executeUpdate();
		
		pstm = conn.prepareStatement("ALTER TABLE " + LiteralSearchTb.TB_NAME + " DROP PRIMARY KEY;");
		pstm.executeUpdate();
		
		//populate table from serialized similar thms indices
		@SuppressWarnings("unchecked")
		List<ListMultimap<String, LiteralSearchIndex>> literalSearchMapList 
			= (List<ListMultimap<String, LiteralSearchIndex>>)FileUtils
				.deserializeListFromFile(DBUtils.LiteralSearchTb.literalSearchIndexMMapPath);
		
		ListMultimap<String, LiteralSearchIndex> literalIndexMMap = literalSearchMapList.get(0);
		
		//construct bytes based on 
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
		.append(",").append(LiteralSearchTb.WORD_INDICES_COL)
		.append(") VALUES(?, ?, ?);") ;		
		
		pstm = conn.prepareStatement(sb.toString());
		
		//Set<Map.Entry<String, byte[]>> entrySet = literalIndicesMMap.entrySet();
		
		Set<String> keySet = literalIndexMMap.keySet();
		
		//Iterator<Map.Entry<String, byte[]>> iter = entrySet.iterator();
		Iterator<String> iter = keySet.iterator();
		int keySetSz = keySet.size();
		//int entrySetSz = entrySet.size();
		
		final int batchSz = 30000;
		System.out.println("Total number of batches: " + Math.ceil(keySetSz/(double)batchSz));
		
		int counter0 = 0;
		while(iter.hasNext()) {		
			System.out.println("About to insert batch " + counter0++);
			for(int i = 0; i < batchSz && iter.hasNext(); i++) {
				
				String word = iter.next();
				Collection<LiteralSearchIndex> searchIndexCol = literalIndexMMap.get(word);
				
				//turn list of LiteralSearchIndex's into two lists of Integers, finally a byte array.
				List<Integer> thmIndexList = new ArrayList<Integer>();
				List<Integer> wordIndexInThmList = new ArrayList<Integer>();
				int counter = maxThmsPerLiteralWord;
				
				for(LiteralSearchIndex searchIndex : searchIndexCol) {
					
					thmIndexList.add(searchIndex.thmIndex());
					byte[] wordIndexAr = searchIndex.wordIndexAr();
					int wordIndexArLen = wordIndexAr.length;
					int upTo = Math.min(wordIndexArLen, wordIndexArTotalLen);
					int j = 0;
					
					//fill list up to LiteralSearchIndex.MAX_WORD_INDEX_AR_LEN, for words
					for(; j < upTo; j++) {
						wordIndexInThmList.add((int)wordIndexAr[j]);
					}
					/* Pad up to MAX_WORD_INDEX_AR_LEN number of indices,
					 * since db retrieval retrieves fixed number of bits at a time. */
					for(; j < wordIndexArTotalLen; j++ ) {
						wordIndexInThmList.add(LiteralSearchIndex.PLACEHOLDER_INDEX);
					}		
					if(--counter < 1) {
						break;
					}
				}
				byte[] thmIndexBytes = SimilarThmUtils.indexListToByteArray(thmIndexList,
						numBitsPerThmIndex, maxThmsPerLiteralWord);
				byte[] wordIndexArBytes = SimilarThmUtils.indexListToByteArray(wordIndexInThmList, 
						numBitsPerWordIndex, maxWordsArListLen);
				
				pstm.setString(1, word);
				pstm.setBytes(2, thmIndexBytes);
				pstm.setBytes(3, wordIndexArBytes);
				
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
