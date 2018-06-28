package com.wolfram.puremath.dbapp;

import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
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

import com.wolfram.puremath.dbapp.DBUtils.AuthorTb;
import com.wolfram.puremath.dbapp.DBUtils.SimilarThmsTb;
import com.wolfram.puremath.dbapp.DBUtils.ThmHypTb;

/**
 * Utilities used for deploying DB.
 * Automatically deploy to multiple tables!
 * 
 * @author yihed
 *
 */
public class DBDeploy {

	private static final Logger logger = LogManager.getLogger(DBDeploy.class);
	
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
	 * Atomically deploys all necessary tables.
	 * Atomicity is achived by creating the new table with updated data with temp name, then rename the 
	 * old table to another temp name, finally rename the updated table to prd name, and delete old table.
	 */
	public static void deployAllTables( ) {
		
		Connection conn = DBUtils.getLocalConnection();
		List<String> tablesCreated = new ArrayList<String>();
		//keys are temp names the prev working tables moved to, values are actual names.
		Map<String, String> tempNameRealNameMap = new HashMap<String, String>();
		
		
			try{
				String authorTbName = AuthorTb.TB_NAME + DBUtils.newNameSuffix;
				AuthorUtils.createAuthorTb(conn, authorTbName);
				//can add for deletion, even though won't exist at the end, since deletion
				//only happens if table exists. This needs to be added in case subsequent 
				//ops get interrupted.
				tablesCreated.add(authorTbName);	
				
				String thmHypTbName = ThmHypTb.TB_NAME + DBUtils.newNameSuffix;
				ThmHypUtils.createThmHypTb(conn, thmHypTbName);
				tablesCreated.add(thmHypTbName);	
				
				/***Done creating new tables, now rename***/
				/*rename at end, so all tables consistent. Outdated PRD table will not be renamed, unless the updated
				 * table can be successfully renamed to the PRD name.*/
				/*author table*/
				String tempName = AuthorTb.TB_NAME + "temp";
				DBUtils.renameTwoTableAtomic(conn, AuthorTb.TB_NAME, tempName, authorTbName, AuthorTb.TB_NAME);
				
				//DBUtils.renameTable(conn, AuthorTb.TB_NAME, tempName);
				tablesCreated.add(tempName);				
				tempNameRealNameMap.put(tempName, AuthorTb.TB_NAME);
				//DBUtils.renameTable(conn, authorTbName, AuthorTb.TB_NAME);
				
				/*thmHypTable*/
				tempName = ThmHypTb.TB_NAME + "temp";
				DBUtils.renameTwoTableAtomic(conn, ThmHypTb.TB_NAME, tempName, thmHypTbName, ThmHypTb.TB_NAME);
				
				//DBUtils.renameTable(conn, ThmHypTb.TB_NAME, tempName);
				tablesCreated.add(tempName);	
				tempNameRealNameMap.put(tempName, ThmHypTb.TB_NAME);
				//DBUtils.renameTable(conn, thmHypTbName, ThmHypTb.TB_NAME);
				
			}catch(SQLException e) {
				//only need to do something if the previous working table got named to name+temp.
				//in which case restore these tables.
				for(Map.Entry<String, String> entry : tempNameRealNameMap.entrySet()) {
					try {
						DBUtils.renameTable(conn, entry.getKey(), entry.getValue());
					}catch(SQLException e1) {
						//pass
					}
				}
				
			}finally {
				//delete tables that were created 
				for(String tableName : tablesCreated) {
					try {
						DBUtils.dropTableIfExists(conn, tableName);
					}catch(SQLException e) {
						//pass these since these are Exceptions during cleanup phase
						
					}
				}				
				DBUtils.silentCloseConn(conn);
			}
					
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
