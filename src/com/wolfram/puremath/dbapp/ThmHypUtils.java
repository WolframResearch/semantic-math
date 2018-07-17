package com.wolfram.puremath.dbapp;

import java.io.File;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import com.wolfram.puremath.dbapp.DBUtils.ThmHypTb;

import thmp.search.SearchCombined.ThmHypPair;

/**
 * This class creates and deploys DB for storing thm and hyp strings, 
 * to save on RAM.
 * @author yihed
 *
 */
public class ThmHypUtils {

	/**
	 * Retrieve thm and hyp statement for given thm index, from db.
	 * Use at app runtime
	 * @param index of thm to retrieve.
	 * @param conn db connection.
	 * @return ThmHypPair.
	 * @throws SQLException
	 */
	public static ThmHypPair getThmHypFromDB(int thmIndex, Connection conn) throws SQLException{
		
		StringBuilder querySb = new StringBuilder(60);
		querySb.append("SELECT ").append(ThmHypTb.THM_COL)
		.append(", ").append(ThmHypTb.HYP_COL)
		.append(", ").append(ThmHypTb.FILE_NAME_COL)
		.append(", ").append(ThmHypTb.THM_TYPE_COL)
		.append(" FROM ").append(ThmHypTb.TB_NAME)
		.append(" WHERE ").append(ThmHypTb.THM_INDEX_COL)
		.append("=").append(thmIndex).append(";");
		
		PreparedStatement pstm = conn.prepareStatement(querySb.toString());
		
		ResultSet rs = pstm.executeQuery();
		//byte[] indexBytes;
		//byte[] wordsIndexArBytes;
		
		String thmStr = "";
		String hypStr = "";
		String fileNameStr = "";
		String thmTypeStr = "";
		
	 	if(rs.next()) {
			//indexBytes = rs.getBytes(DBUtils.LiteralSearchTb.THM_INDICES_COL);
			//wordsIndexArBytes = rs.getBytes(DBUtils.LiteralSearchTb.WORD_INDICES_COL);
			
			thmStr = rs.getString(ThmHypTb.THM_COL);
			hypStr = rs.getString(ThmHypTb.HYP_COL);
			fileNameStr = rs.getString(ThmHypTb.FILE_NAME_COL);
			thmTypeStr = rs.getString(ThmHypTb.THM_TYPE_COL);
		}else {
			pstm.close();
			return ThmHypPair.PLACEHOLDER_PAIR();
		}
		pstm.close();
		rs.close();
		
		//thmIndexList.addAll(SimilarThmUtils.byteArrayToIndexList(indexBytes, numBitsPerThmIndex));
		//wordsIndexArList.addAll(SimilarThmUtils.byteArrayToIndexList(wordsIndexArBytes, numBitsPerWordIndex));
		
		return new ThmHypPair(thmStr, hypStr, fileNameStr, thmTypeStr);
	}
	
	/**
	 * Retrieve thm and hyp statement for given list of thm index, from db.
	 * Batch calls to save time.
	 * Use at app runtime
	 * @param index of thm to retrieve.
	 * @param conn db connection.
	 * @return ThmHypPair.
	 * @throws SQLException
	 */
	public static List<ThmHypPair> getThmHypFromDB(List<Integer> thmIndexList, Connection conn) throws SQLException{
		
		if(thmIndexList.isEmpty()) {
			return Collections.emptyList();
		}
		
		StringBuilder thmIndexSb = new StringBuilder(400);
		//note this query result does not preserve the order as in thmIndexList!
		for(int thmIndex : thmIndexList) {
			thmIndexSb.append(ThmHypTb.THM_INDEX_COL)
			.append("=")
			.append(thmIndex)
			.append(" OR ");			
		}
		thmIndexSb.delete(thmIndexSb.length()-4, thmIndexSb.length());
		
		StringBuilder querySb = new StringBuilder(60);
		querySb.append("SELECT ").append(ThmHypTb.THM_INDEX_COL)
		.append(", ").append(ThmHypTb.THM_COL)
		.append(", ").append(ThmHypTb.HYP_COL)
		.append(", ").append(ThmHypTb.FILE_NAME_COL)
		.append(", ").append(ThmHypTb.THM_TYPE_COL)
		.append(" FROM ").append(ThmHypTb.TB_NAME)
		.append(" WHERE ").append(thmIndexSb)
		.append(";");
		
		PreparedStatement pstm = conn.prepareStatement(querySb.toString());
		
		//byte[] indexBytes;
		//byte[] wordsIndexArBytes;
		int thmIndex = 0;
		String thmStr = "";
		String hypStr = "";
		String fileNameStr = "";
		String thmTypeStr = "";
		
		ResultSet rs = pstm.executeQuery();
		List<ThmHypPair> thmHypPairList = new ArrayList<ThmHypPair>();
		ThmHypPairComparator comp = new ThmHypPairComparator(thmIndexList) ;
		TreeMap<Integer, ThmHypPair> thmHypPairMap = new TreeMap<Integer, ThmHypPair>(comp);
		
	 	while(rs.next()) {
			//indexBytes = rs.getBytes(DBUtils.LiteralSearchTb.THM_INDICES_COL);
			//wordsIndexArBytes = rs.getBytes(DBUtils.LiteralSearchTb.WORD_INDICES_COL);
			thmIndex = rs.getInt(ThmHypTb.THM_INDEX_COL);
			thmStr = rs.getString(ThmHypTb.THM_COL);
			hypStr = rs.getString(ThmHypTb.HYP_COL);
			fileNameStr = rs.getString(ThmHypTb.FILE_NAME_COL);
			thmTypeStr = rs.getString(ThmHypTb.THM_TYPE_COL);
			
			thmHypPairMap.put(thmIndex, new ThmHypPair(thmStr, hypStr, fileNameStr, thmTypeStr));			
		}
	 	
	 	thmHypPairList.addAll(thmHypPairMap.values());
	 	
		pstm.close();
		rs.close();
		
		//thmIndexList.addAll(SimilarThmUtils.byteArrayToIndexList(indexBytes, numBitsPerThmIndex));
		//wordsIndexArList.addAll(SimilarThmUtils.byteArrayToIndexList(wordsIndexArBytes, numBitsPerWordIndex));
		
		return thmHypPairList;
	}
	
	private static class ThmHypPairComparator implements Comparator<Integer>{
		
		//map of thm indices and their ranking, lower means higher-ranked.
		Map<Integer, Integer> indexOrderMap = new HashMap<Integer, Integer>();
		ThmHypPairComparator(List<Integer> indexList){
			int ranking = 0;
			for(int index : indexList) {
				indexOrderMap.put(index, ranking++);
			}
		}
		
		//want lower ranks to have priority.
		@Override
		public int compare(Integer pair1, Integer pair2) {
			Integer rank1 = indexOrderMap.get(pair1);
			Integer rank2 = indexOrderMap.get(pair2);
			
			if(rank1 == null) {
				return 1;
			}
			if(rank2 == null) {
				return -1;
			}
			
			return Integer.compare(rank1, rank2);			
		}		
		
	}
	
	/**
	 * Need 3 columns, thm index, .
	 * 
	 * Deploy to the thm hyp db table.
	 * Use before app runtime.
	 * 
	 * @param conn
	 * @throws SQLException
	 */
	public static void populateThmHypTb(Connection conn, String dataRootDirPath) throws SQLException {
		
		/*If creating table, should be created as e.g.
		 * CREATE TABLE literalSearchTb (word VARCHAR(15), thmIndices VARBINARY(789), wordIndices VARBINARY(600))
		 * 20*15/8 = 37.5*/
		//accomodate changes in number of indices and number of concepts to display!!!
		
		//int maxThmsPerLiteralWord = Searcher.SearchMetaData.maxThmsPerLiteralWord;
		//number of bytes per list. As of Dec 29, num bytes for thmIndices is 300*21/8 = 787.5 + 2 ~ 789
		//For wordIndices it is 300 * 2 * 8/8 = 600
		//int varbinaryLen = maxThmsPerLiteralWord * numBitsPerThmIndex / DBUtils.NUM_BITS_PER_BYTE;
		
		//1 for rounding, 1 extra
		//final int padding = 2;
		//varbinaryLen += padding;
		
		PreparedStatement pstm;
		
		pstm = conn.prepareStatement("TRUNCATE " + ThmHypTb.TB_NAME + ";");		
		pstm.executeUpdate();
		
		//need to update table MEDIUMINT(9) UNSIGNED;  VARBINARY(265)
		//int wordIndexArTotalLen = LiteralSearchIndex.MAX_INDEX_COUNT_PER_WORD; //this value is 2
		//int maxWordsArListLen = maxThmsPerLiteralWord * wordIndexArTotalLen;
		
		//int wordArVarBinaryLen = maxWordsArListLen * numBitsPerWordIndex / DBUtils.NUM_BITS_PER_BYTE;
		//"ALTER TABLE <table_name> MODIFY <col_name> VARCHAR(65);";
		pstm = conn.prepareStatement("ALTER TABLE " + ThmHypTb.TB_NAME 
				+ " MODIFY " + ThmHypTb.THM_INDEX_COL + " INTEGER,"
				+ " MODIFY " + ThmHypTb.THM_COL + " VARCHAR(" + ThmHypTb.maxThmColLen + "),"
				+ " MODIFY " + ThmHypTb.HYP_COL + " VARCHAR(" + ThmHypTb.maxHypColLen + "),"
				+ " MODIFY " + ThmHypTb.FILE_NAME_COL + " VARCHAR(" + ThmHypTb.maxFileNameLen + "),"
				+ " MODIFY " + ThmHypTb.THM_TYPE_COL + " VARCHAR(" + ThmHypTb.maxThmTypeLen + ");");
		
		pstm.executeUpdate();
		
		pstm = conn.prepareStatement("ALTER TABLE " + ThmHypTb.TB_NAME + " DROP PRIMARY KEY;");
		pstm.executeUpdate();
		
		//populate table from serialized thmhyp pairs
		
		File[] thmHypPairsFiles = new File(dataRootDirPath, ThmHypTb.thmHypPairsDirRelPath).listFiles();
		//all files take form combinedParsedExpressionList + index
		int thmHypPairsFilesSz = thmHypPairsFiles.length;
		
		/*@SuppressWarnings("unchecked")
		List<ListMultimap<String, LiteralSearchIndex>> literalSearchMapList 
			= (List<ListMultimap<String, LiteralSearchIndex>>)FileUtils
				.deserializeListFromFile(DBUtils.LiteralSearchTb.literalSearchIndexMMapPath);*/
		
		//ListMultimap<String, LiteralSearchIndex> literalIndexMMap = literalSearchMapList.get(0);
		
		StringBuilder sb = new StringBuilder(50);
		
		sb.append("ALTER TABLE ").append(ThmHypTb.TB_NAME)
		.append(" ADD PRIMARY KEY (")
		.append(ThmHypTb.THM_INDEX_COL).append(");");
		
		pstm = conn.prepareStatement(sb.toString());
		pstm.executeUpdate();
		
		sb = new StringBuilder(50);
		
		sb.append("INSERT INTO " + ThmHypTb.TB_NAME + " (")
		.append(ThmHypTb.THM_INDEX_COL)
		.append(",").append(ThmHypTb.THM_COL)
		.append(",").append(ThmHypTb.HYP_COL)
		.append(",").append(ThmHypTb.FILE_NAME_COL)
		.append(",").append(ThmHypTb.THM_TYPE_COL)
		.append(") VALUES(?, ?, ?, ?, ?);") ;	
		
		pstm = conn.prepareStatement(sb.toString());
		
		//Set<String> keySet = literalIndexMMap.keySet();
		
		//Iterator<Map.Entry<String, byte[]>> iter = entrySet.iterator();
		//Iterator<String> iter = keySet.iterator();
		//int keySetSz = keySet.size();
		//int entrySetSz = entrySet.size();
		
		//final int batchSz = 15000;
		int thmHypPairsFilesSzHalf = thmHypPairsFilesSz/2;
		
		System.out.println("Total number of batches: " + Math.ceil(thmHypPairsFilesSz/2.));
		
		int thmCounter = 0;
		
		int j = 0;
		for(; j < thmHypPairsFilesSzHalf; j++) {
			
			List<ThmHypPair> thmHypPairList = new ArrayList<ThmHypPair>();
			//add two at a time, to save time in deployment
			@SuppressWarnings("unchecked")
			List<ThmHypPair> thmHypPairList1 = (List<ThmHypPair>)DBUtils
				.deserializeListFromFile(ThmHypTb.thmHypPairsDirRelPath + ThmHypTb.thmHypPairsNameRoot + 2*j);
			
			thmHypPairList.addAll(thmHypPairList1);
			
			if(2*j + 1 < thmHypPairsFilesSz) {
				@SuppressWarnings("unchecked")
				List<ThmHypPair> thmHypPairList2 = (List<ThmHypPair>)DBUtils
					.deserializeListFromFile(ThmHypTb.thmHypPairsDirRelPath + ThmHypTb.thmHypPairsNameRoot + (2*j+1));
				thmHypPairList.addAll(thmHypPairList2);
			}
			
			System.out.println("About to insert batch " + j);
			thmCounter = deployThmHypBatch(pstm, thmCounter, thmHypPairList);			
		}
		
		//in case thmHypPairsFilesSz is odd
		if(2*j < thmHypPairsFilesSz) {
			@SuppressWarnings("unchecked")
			List<ThmHypPair> thmHypPairList = (List<ThmHypPair>)DBUtils
				.deserializeListFromFile(ThmHypTb.thmHypPairsDirRelPath + ThmHypTb.thmHypPairsNameRoot + (2*j));
			System.out.println("About to insert batch " + (j));
			deployThmHypBatch(pstm, thmCounter, thmHypPairList);	
		}
		
	}

	private static int deployThmHypBatch(PreparedStatement pstm, int thmCounter, List<ThmHypPair> thmHypPairList)
			throws SQLException {
		for(ThmHypPair thmHypPair : thmHypPairList) {
			
			int thmIndex = thmCounter++;
			String thmStr = thmHypPair.thmStr();
			String hypStr = thmHypPair.hypStr();
			String fileName = thmHypPair.srcFileName();
			String thmType = thmHypPair.thmType();
			
			//turn list of LiteralSearchIndex's into two lists of Integers, finally a byte array.
			/*List<Integer> thmIndexList = new ArrayList<Integer>();
			List<Integer> wordIndexInThmList = new ArrayList<Integer>();
			int counter = maxThmsPerLiteralWord;*/
			
			pstm.setInt(1, thmIndex);
			pstm.setString(2, thmStr);
			pstm.setString(3, hypStr);
			pstm.setString(4, fileName);
			pstm.setString(5, thmType);
			
			pstm.addBatch();
		}
		pstm.executeBatch();
		return thmCounter;
	}
	
	public static void createThmHypTb(Connection conn) throws SQLException {
		createThmHypTb(conn, ThmHypTb.TB_NAME);
	}
	
	/**
	 * Create the ThmHypTb table.
	 * @param conn
	 * @throws SQLException
	 */
	public static void createThmHypTb(Connection conn, String tableName) throws SQLException {
		
		PreparedStatement pstm;
		StringBuilder sb = new StringBuilder(50);
		
		DBUtils.dropTableIfExists(conn, tableName);
		
		//CREATE TABLE pet (name VARCHAR(20), owner VARCHAR(20),
		//	    -> species VARCHAR(20), sex CHAR(1), birth DATE, death DATE);
		//CREATE TABLE literalSearchTb (word VARCHAR(15), thmIndices VARBINARY(789), wordIndices VARBINARY(600))
		
		sb.setLength(0);
		sb.append("CREATE TABLE ").append(tableName)
		.append(" (").append(ThmHypTb.THM_INDEX_COL).append(" INTEGER, ")
		.append(ThmHypTb.THM_COL).append(" VARCHAR(" + ThmHypTb.maxThmColLen + "),")
		.append(ThmHypTb.HYP_COL).append(" VARCHAR(" + ThmHypTb.maxHypColLen + "),")
		.append(ThmHypTb.FILE_NAME_COL).append(" VARCHAR(" + ThmHypTb.maxFileNameLen + "),")
		.append(ThmHypTb.THM_TYPE_COL).append(" VARCHAR(" + ThmHypTb.maxThmTypeLen + "));");
		
		pstm = conn.prepareStatement(sb.toString());
		pstm.executeUpdate();
		
		sb = new StringBuilder(50);
		
		sb.append("ALTER TABLE ").append(tableName)
		.append(" ADD PRIMARY KEY (")
		.append(ThmHypTb.THM_INDEX_COL).append(");");
		
		pstm = conn.prepareStatement(sb.toString());
		pstm.executeUpdate();
		
	}
	
	/** Deletes the ThmHypTb table.
	 * @param conn
	 * @throws SQLException
	 */
	public static void dropThmHypTb(Connection conn) throws SQLException {
		
		//DROP [TEMPORARY] TABLE [IF EXISTS]
	    //tbl_name
		PreparedStatement pstm;
		
		pstm = conn.prepareStatement("DROP TABLE IF EXISTS " + ThmHypTb.TB_NAME + ";");		
		pstm.executeUpdate();		
	}
	
	
}
