package com.wolfram.puremath.dbapp;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.wolfram.puremath.dbapp.DBUtils.SimilarThmsTb;

/**
 * Utilities used for deploying DB.
 * Automatically deploy to multiple tables!
 * 
 * @author yihed
 *
 */
public class DBDeploy {

	private static final Logger logger = LogManager.getLogger(DBDeploy.class);
	
	/**
	 * Truncates the table , populates it with data from supplied csv file.
	 * @param csvFilePath Path to CSV file containing data to populate table with, resort
	 * to default file if none supplied. e.g. "metaDataNameDB.csv"
	 * @throws SQLException 
	 */
	public static void populateAuthorTb(Connection conn, String dataRootDirPath) throws SQLException {
		
		PreparedStatement pstm;
		//don't like using .toString(), but only option!
		String authorTbCsvAbsPath = Paths.get(dataRootDirPath, DBUtils.AUTHOR_TB_CSV_REL_PATH).toString();
		
		pstm = conn.prepareStatement("TRUNCATE " + DBUtils.AUTHOR_TB_NAME + ";");		
		pstm.executeUpdate();
		
		//ALTER TABLE user_customer_permission DROP PRIMARY KEY;
		pstm = conn.prepareStatement("ALTER TABLE " + DBUtils.AUTHOR_TB_NAME + " DROP PRIMARY KEY;");
		pstm.executeUpdate();
		
		List<String> indexList = DBUtils.getAuthorTbIndexes();
		for(String index : indexList) {
			//including quotes result in syntax error. Do not setting string.
			pstm = conn.prepareStatement("DROP INDEX " + index + " ON " + DBUtils.AUTHOR_TB_NAME + ";");		
			pstm.executeUpdate();
		}		
				
		/*LOAD DATA INFILE  command
		 * mysql> LOAD DATA INFILE "/usr/share/tomcat/webapps/theoremSearchTest/src/thmp/data/metaDataNameDB.csv" 
		 * INTO TABLE authorTb COLUMNS TERMINATED BY "," OPTIONALLY ENCLOSED BY "'" ESCAPED BY "\\";
		 */		
		pstm = conn.prepareStatement("LOAD DATA INFILE ? INTO TABLE " + DBUtils.AUTHOR_TB_NAME 
				+ " COLUMNS TERMINATED BY ',' OPTIONALLY ENCLOSED BY '\'' ESCAPED BY '\\';");
		
		pstm.setString(1, authorTbCsvAbsPath);
		
		int rowsCount = pstm.executeUpdate();
		logger.info("ResultSet from loading csv data: "+rowsCount);
		
		//add keys back
		//CREATE INDEX authorIndex ON authorTb (author);
		StringBuilder keySb = new StringBuilder(200);
		keySb.append("ALTER TABLE ").append(DBUtils.AUTHOR_TB_NAME)
		.append(" ADD PRIMARY KEY (")
		.append(DBUtils.AUTHOR_TB_THMID_COL).append(",")
		.append(DBUtils.AUTHOR_TB_FIRSTNAME_COL).append(",")
		.append(DBUtils.AUTHOR_TB_MIDDLENAME_COL).append(",")
		.append(DBUtils.AUTHOR_TB_LASTNAME_COL).append(");");
		pstm = conn.prepareStatement(keySb.toString());
		
		pstm.executeUpdate();
		
		for(String index : indexList) {
			pstm = conn.prepareStatement("CREATE INDEX " + index + " ON " + DBUtils.AUTHOR_TB_NAME 
					+ " (" + DBUtils.AUTHOR_TB_NAME + ");");	
			pstm.executeUpdate();
		}		
	}
	
	public static void populateSimilarThmsTb(Connection conn) throws SQLException {
		
		/*If creating table, should be created as 
		 * CREATE TABLE similarThmsTb (index MEDIUMINT(9) UNSIGNED, similarThms VARBINARY(252));*/
		
		PreparedStatement pstm;
		
		pstm = conn.prepareStatement("TRUNCATE " + SimilarThmsTb.TB_NAME + ";");		
		pstm.executeUpdate();
		
		//252 ~ 20 * 100 / 8
		//need to pudate table MEDIUMINT(9) UNSIGNED;  VARBINARY(265)
		//"ALTER TABLE <table_name> MODIFY <col_name> VARCHAR(65);";		
		int varbinaryLen = SimilarThmUtils.maxSimilarThmListStrLen();
		pstm = conn.prepareStatement("ALTER TABLE " + SimilarThmsTb.TB_NAME + " MODIFY " 
				+ SimilarThmsTb.SIMILAR_THMS_COL + " VARBINARY(" + varbinaryLen + ");");
		pstm.executeUpdate();
		
		pstm = conn.prepareStatement("ALTER TABLE " + SimilarThmsTb.TB_NAME + " DROP PRIMARY KEY;");
		pstm.executeUpdate();
		
		//populate table from serialized similar thms indices
		@SuppressWarnings("unchecked")
		List<Map<Integer, byte[]>> similarThmsMapList 
			= (List<Map<Integer, byte[]>>)DBUtils.deserializeListFromFile(DBUtils.SimilarThmsTb.similarThmCombinedIndexByteArrayPath);
		
		Map<Integer, byte[]> similarThmsMap = similarThmsMapList.get(0);
		
		StringBuilder sb = new StringBuilder(50);
		
		sb.append("ALTER TABLE ").append(SimilarThmsTb.TB_NAME)
		.append(" ADD PRIMARY KEY (")
		.append(SimilarThmsTb.INDEX_COL).append(");");
		
		pstm = conn.prepareStatement(sb.toString());
		pstm.executeUpdate();
		sb = new StringBuilder(50);
		sb.append("INSERT INTO " + SimilarThmsTb.TB_NAME + " (")
		.append(SimilarThmsTb.INDEX_COL)
		.append(",").append(SimilarThmsTb.SIMILAR_THMS_COL)
		.append(") VALUES(?, ?);");
		
		pstm = conn.prepareStatement(sb.toString());
		
		Set<Map.Entry<Integer, byte[]>> entrySet = similarThmsMap.entrySet();
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
				byte[] similarIndexBytes = entry.getValue();
				pstm.setInt(1, thmIndex);
				pstm.setBytes(2, similarIndexBytes);
				pstm.addBatch();
			}
			pstm.executeBatch();			
		}
		
		/*for(Map.Entry<Integer, byte[]> entry : similarThmsMap.entrySet()) {
			int thmIndex = entry.getKey();
			byte[] similarIndexBytes = entry.getValue();
			pstm.setInt(1, thmIndex);
			pstm.setBytes(2, similarIndexBytes);
			pstm.executeUpdate();
		}*/
	}
	
	/**
	 * Deploys db tables. 
	 * @param absolute path to directory containing data parent directory src/thmp/data.
	 * @throws SQLException
	 */
	private static void deployAllTables(String dirpath) throws SQLException{
		Connection conn = DBUtils.getLocalConnection();
		
		//June18: don't deploy to DB, populateSimilarThmsTb(conn);
		
		//temporary comment out!! June populateAuthorTb(conn);
		//ThmHypUtils.createThmHypTb(conn);
		ThmHypUtils.populateThmHypTb(conn, dirpath);
		
		//June13: don't deploy to DB, redundant wrt wordmap LiteralSearchUtils.populateLiteralSearchTb(conn);
		
		conn.close();
	}
	
	/**
	 * sets command-line option values
	 * 
	 * @return CommandLine object to 
	 * @throws ParseException 
	 */
	private static CommandLine parseOpt(String[] args) throws ParseException {
		Options opt = new Options();
		opt.addOption("dirpath", false, 
				"absolute path to directory containing data parent directory src/thmp/data");
		
		CommandLineParser parser = new DefaultParser();
		return parser.parse(opt, args);		
	}
	
	public static void main(String[] args) throws SQLException, ParseException {
		
		CommandLine cmd = null;
		String dirPath;
		
		try {
			cmd = parseOpt(args);
			dirPath = cmd.getOptionValue("dirpath");
		}catch(ParseException e) {
			System.out.println("Can't parse options. Using default values. Path: " + DBUtils.defaultDirPath);
			dirPath = DBUtils.defaultDirPath;
		}
		
		deployAllTables(dirPath);
		
		/*
		 * boolean b = false;
		if(b) {
			if(args.length < 1) {
				System.out.println("Please enter the path to a csv file to populate the database.");
				return;
			}
			String filePath = args[0];
			
			populateAuthorTb(conn, filePath);
		}
		 */
	}
	
}
