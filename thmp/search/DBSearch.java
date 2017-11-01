package thmp.search;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import thmp.utils.DBUtils;
import thmp.utils.DBUtils.AuthorName;
import thmp.utils.DBUtils.ConjDisjType;
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
	
	//private static final Pattern CONJ_DISJ_PATT = Pattern.compile("(?:and|or)");
	
	/**
	 * Tree structure used to represent relations between authors in query,
	 * i.e. nested conjunctions and disjunctions.
	 * Creates tree structure with relations.
	 */
	public static class AuthorRelation{
		
		AuthorRelationNode rootNode;
		String sqlQueryStr; 
		/**used for constructing JOIN's*/
		int numAuthors;
		
		/**
		 * @param userInput e.g. (A. Author1 and (B. Author2 or C Author3)). 
		 */
		public AuthorRelation(String userInput) {
			
			this.rootNode = parseAuthorStr(userInput);
			this.numAuthors = this.rootNode.numAuthors;
			System.out.println("numAuthors "+this.numAuthors);
			
			int curLevelNameListSz = this.rootNode.childrenList.size();
			int typeListSz = this.rootNode.childRelationList.size();
			if(curLevelNameListSz != typeListSz+1) {
				throw new IllegalArgumentException("Mismatched authors and conj/disj operators");
			}
			
			//StringBuilder sqlSb = new StringBuilder(300);
			//SELECT t1.thmId FROM (authorTb AS t1,authorTb AS t2) WHERE t1.author='author2' AND t2.author='author1';
			StringBuilder querySb = new StringBuilder("SELECT t0.").append(THM_ID_COL).append(" FROM (")
					;					
			
			for(int i = 0; i < this.numAuthors; i++) {
				querySb.append(AUTHOR_TABLE_NAME)
				.append(" AS t")
				.append(i)
				.append(", ");				
			}
			int sbLen = querySb.length();
			querySb.delete(sbLen-2, sbLen);
			
			querySb
			.append(")")
			.append(" WHERE ");
			
			int authorCounter = 0;
			//build sql query str
			buildSqlStr(this.rootNode, querySb, authorCounter);
			querySb.append(";");
			this.sqlQueryStr = querySb.toString();
			logger.info("searching author - query created: "+querySb);
		}
		
		/**
		 * Builds sql query string.
		 * @param rootNode2
		 * @param sqlSb
		 * @param authorCounter used for JOIN's
		 * @return updated authorCounter
		 */
		private int buildSqlStr(AuthorRelationNode node, StringBuilder sqlSb, int authorCounter) {
			
			sqlSb.append("(");
			List<AuthorRelationNode> childrenList = node.childrenList;
			List<ConjDisjType> typeList = node.childRelationList;
			
			int childrenListSz = childrenList.size();
			int typeListSz = typeList.size();
			
			for(int i = 0; i < typeListSz; i++) {
				AuthorRelationNode curNode = childrenList.get(i);
				ConjDisjType type = typeList.get(i);
				
				if(curNode.hasChild()) {
					authorCounter = buildSqlStr(curNode, sqlSb, authorCounter);
				}else {
					/*
					 createAuthorQuery(String relationStr, StringBuilder querySb, AuthorName author,
			boolean abbreviateNameBool)
					 */
					createAuthorQuery(type.getDbName(), sqlSb, curNode.authorName, authorCounter, true);	
					authorCounter++;
				}
			}
			AuthorRelationNode lastNode = childrenList.get(childrenListSz-1);
			if(lastNode.hasChild()) {
				authorCounter = buildSqlStr(lastNode, sqlSb, authorCounter);
			}else {
				//dummy OR
				createAuthorQuery("OR", sqlSb, lastNode.authorName, authorCounter, true);
				authorCounter++;
				int sbLen = sqlSb.length();
				//-3 because of space at end
				sqlSb.delete(sbLen-3, sbLen);
			}
			sqlSb.append(")");
			return authorCounter;
		}

		/**
		 * Parse author string, creates tree.
		 * @param userInput Includes all parentheses user enters.
		 * @return rootNode to parse
		 */
		private static AuthorRelationNode parseAuthorStr(String str) {
			AuthorRelationNode rootNode = new AuthorRelationNode( );
			int strLen = str.length();
			int index = 0;
			while(index < strLen && str.charAt(index) == ' ') {
				index++;
			}
			parseAuthorStr(str, index, rootNode);
			return rootNode;
		}
		
		/**
		 * 
		 * @param userInput
		 * @param index
		 * @param parentNode
		 * @return Updated index in input. Index is the last char processed.
		 */
		private static int parseAuthorStr(String str, int index, AuthorRelationNode parentNode) {
			
			int strLen = str.length();
			//one name at a time
			StringBuilder nameSb = new StringBuilder(30);
			List<String> curLevelNameList = new ArrayList<String>();
			List<ConjDisjType> curLevelTypeList = new ArrayList<ConjDisjType>();
			
			ConjDisjType conjDisjType = null;
			boolean typeChanged = false;
			
			//append to parent node according to parse
			while(index < strLen) {
				char curChar = str.charAt(index);
				if(index + 5 < strLen) {
					boolean nameEnded = false;
					if((str.substring(index, index+5).equals(" and ") )) {
						if(conjDisjType == ConjDisjType.DISJ) {
							typeChanged = true;
						}
						nameEnded = true;
						//take care of e.g. "and and"
						curLevelTypeList.add(ConjDisjType.CONJ);
						parentNode.addChildConjDisjType(ConjDisjType.CONJ);
						index += 5;
						//conjDisjType = ConjDisjType.CONJ;
					}else if (str.substring(index, index+4).equals(" or ")){
						if(conjDisjType == ConjDisjType.CONJ) {
							typeChanged = true;
						}
						curLevelTypeList.add(ConjDisjType.DISJ);
						parentNode.addChildConjDisjType(ConjDisjType.DISJ);
						nameEnded = true;
						index += 4;
						//conjDisjType = ConjDisjType.DISJ;
					}
					if(nameEnded) {
						if(nameSb.length() == 0) {
							curLevelTypeList.remove(curLevelTypeList.size()-1);
							parentNode.childRelationList.remove(parentNode.childRelationList.size()-1);
						}else {
							curLevelNameList.add(nameSb.toString());
							parentNode.addChildNode(new AuthorRelationNode(nameSb.toString()));
							parentNode.numAuthors++;
						}
						nameSb = new StringBuilder(30);
						continue;
					}
				}
				curChar = str.charAt(index);
				if(curChar == '(') {
					//take care if ( doens't follow and/or !!!
					
					//parse sb and append children of current level
					//parseAndAppendChildren(curLevelNameList, curLevelTypeList, parentNode);
					AuthorRelationNode childNode = new AuthorRelationNode( );
					index = parseAuthorStr(str, index+1, childNode) + 1;	
					
					parentNode.addChildNode(childNode);
					parentNode.numAuthors += childNode.numAuthors;
				}//or is switching conj disj type
				else if(curChar == ')') {
					//index++;
					curLevelNameList.add(nameSb.toString());
					//note this could cause double nesting if specified in (  )
					parentNode.addChildNode(new AuthorRelationNode(nameSb.toString()));
					parentNode.numAuthors++;
					return ++index;
				}
				else {
					nameSb.append(curChar);
					index++;
				}			
			}
			if(nameSb.length() > 0) {
				curLevelNameList.add(nameSb.toString());
				parentNode.addChildNode(new AuthorRelationNode(nameSb.toString()));
				parentNode.numAuthors++;
			}
			return index;
		}
		
	}
	
	//parse, no parentheses. Same conj disj type, therefore All same level. 
	//should contruct sql query str!
	private static void parseAndAppendChildren(List<String> curLevelNameList, 
			List<ConjDisjType> curLevelTypeList, AuthorRelationNode parentNode) {
		
		int curLevelNameListSz = curLevelNameList.size();
		if(curLevelNameListSz == 0) {
			return;
		}
		int typeListSz = curLevelTypeList.size();
		if(curLevelNameListSz != typeListSz && curLevelNameListSz != typeListSz+1) {
			throw new IllegalArgumentException("Mismatched authors and conj/disj operators");
		}
		//String[] strAr = CONJ_DISJ_PATT.split(str);
		for(int i = 0; i < curLevelNameListSz; i++) {
			//create and add child node, all are leaves
			parentNode.addChildNode(new AuthorRelationNode(curLevelNameList.get(i)));	
		}
		parentNode.addChildConjDisjType(curLevelTypeList);
	}
	
	private static class AuthorRelationNode{
		
		/*same type between all children, i.e. not "A and B or C". Defaults to DISJ*/
		//ConjDisjType conjDisjType = ConjDisjType.DISJ;
		//either author name or list of children
		AuthorName authorName;
		List<AuthorRelationNode> childrenList = new ArrayList<AuthorRelationNode>();
		List<ConjDisjType> childRelationList = new ArrayList<ConjDisjType>();
		//used for constructing JOINs
		int numAuthors;
		
		/**
		 * @param childrenStrList List of author name strings for this node.
		 * @param userInput e.g. (A. Author1 and (B. Author2 or C Author3)). 
		 */
		AuthorRelationNode(List<String> childrenStrList, List<ConjDisjType> childRelationList) {
			
			assert childRelationList.size() == childrenStrList.size() - 1;
			//childrenList = new ArrayList<AuthorRelationNode>();
			for(String str : childrenStrList) {
				
			}
			
		}
		
		AuthorRelationNode() {
		}
		
		/**
		 * For leaf node.
		 * @param authorName
		 */
		AuthorRelationNode(String authorName) {
			this.authorName = new AuthorName(authorName);
		}
		
		/**
		 * @param userInput e.g. (A. Author1 and (B. Author2 or C Author3)). 
		 */
		//AuthorRelationNode(AuthorName authorName, List<AuthorRelationNode> childrenList, ConjDisjType conjDisjType) {
			
		//}
		
		boolean hasChild() {
			return !childrenList.isEmpty();
		}
		
		public void addChildNode(AuthorRelationNode childNode) {
			childrenList.add(childNode);
		}
		
		public void addChildConjDisjType(List<ConjDisjType> typeList) {
			childRelationList.addAll(typeList);
		}
		
		public void addChildConjDisjType(ConjDisjType type) {
			childRelationList.add(type);
		}
	}
	
	/**
	 * Return list of thms satisfying authorship criteria, by searching 
	 * the database.
	 * Database rows have form "'1710.01688','Benjamin','L','Recht'"
	 * @param authorList
	 * @param conjDisjType
	 * @return List of indices of thms, *not* their ID's!
	 * @throws SQLException 
	 */
	public static List<Integer> searchByAuthor(AuthorRelation authorRelation, DBUtils.ConjDisjType conjDisjType) throws SQLException {
		//should pass in first name last name instead!
		
		/*if(authorList.size() == 0) {
			throw new IllegalArgumentException("List of authors cannot be empty!");
		} *********** */
		
		String relationStr = conjDisjType.getDbName();
		//data need to be normalized, e.g. M. L. Mangano
		
		//e.g. "SELECT thmId FROM authorTb3 WHERE (author='W. N. Kang' OR author='S. Ivanov');"
		//make DB call, get default connection
		Connection conn = DBUtils.getPooledConnection();
		boolean abbreviateName = false;
		List<Integer> dbList = queryWithAuthors(authorRelation, conn, abbreviateName);
		if(false && dbList.isEmpty()) {
			//replace author list with just first initials, and query again
			/*List<AuthorName> authorList2 = new ArrayList<AuthorName>();
			for(AuthorName authorName : authorList) {
				authorList2.add(authorName.abbreviateName());
			}*/
			abbreviateName = true;
			dbList = queryWithAuthors(authorRelation, conn, abbreviateName);
		}
		//what if still no hits, gradually reduce down the number of names tried?! <--yes
		
		DBUtils.closePooledConnection(conn);
		return dbList;
	}

	/**
	 * Make db Query 
	 * @param authorList
	 * @param dbList
	 * @param relationStr
	 * @param conn
	 * @param 
	 * @throws SQLException
	 * @deprecated
	 */
	private static List<Integer> queryWithAuthors0(List<AuthorName> authorList,  String relationStr,
			Connection conn, boolean abbreviateNameBool) throws SQLException {
		
		List<Integer> dbList = new ArrayList<Integer>();
		
		StringBuilder querySb = new StringBuilder("SELECT ").append(THM_ID_COL).append(" FROM " + AUTHOR_TABLE_NAME 
				+ " WHERE (");
		//decide between first and last author names
		
		//first try all of first name, if no result, try initial 
		//make separate db calls for each author
		
		//now assume only last name.
		for(AuthorName author : authorList) {
			
			//createAuthorQuery(relationStr, querySb, author, abbreviateNameBool);
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

	private static List<Integer> queryWithAuthors(AuthorRelation authorRelation,
			Connection conn, boolean abbreviateNameBool) throws SQLException {
		
		List<Integer> dbList = new ArrayList<Integer>();
		//StringBuilder querySb = new StringBuilder(300);
		/*StringBuilder querySb = new StringBuilder("SELECT ").append(THM_ID_COL).append(" FROM " + AUTHOR_TABLE_NAME 
				+ " WHERE "); ******/
		//decide between first and last author names
		
		//first try all of first name, if no result, try initial 
		//make separate db calls for each author
		
		//now assume only last name.
		/*for(AuthorName author : authorList) {
			createAuthorQuery(relationStr, querySb, author, abbreviateNameBool);
		}*/
		//querySb.delete(querySb.length() - relationStr.length(), querySb.length());
		//querySb.append(");");
		//querySb.append(authorRelation.sqlQueryStr);
		String queryStr = authorRelation.sqlQueryStr;
		
		//**********querySb.append(";");
		logger.info("searching author - query created: "+queryStr);
		
		PreparedStatement stm = conn.prepareStatement(queryStr);
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
	/**
	 * Create author query given an AuthorName.
	 * @param relationStr
	 * @param querySb
	 * @param author
	 */
	private static void createAuthorQuery(String relationStr, StringBuilder querySb, AuthorName author,
			int authorCounter, boolean abbreviateNameBool) {
		
		boolean nameAppended = false;
		String firstName = author.firstName();
		if(!abbreviateNameBool && firstName.length() > 1) {
			if(!"".equals(firstName) ) {
				querySb.append(" (t").append(authorCounter).append(".").append(FIRST_NAME_COL)
				.append("='").append(firstName).append("' ");
				nameAppended = true;
			}
			String middleInitial = author.middleInitial();
			if(!"".equals(middleInitial) ) {
				if(nameAppended) {
					//querySb.append(" AND ").append(MIDDLE_NAME_COL).append("='").append(middleInitial).append("' ");	
					querySb.append(" AND t").append(authorCounter).append(".").append(MIDDLE_NAME_COL).append(" LIKE '").append(middleInitial).append("%' ");
				}else {
					querySb.append(" t").append(authorCounter).append(".").append(MIDDLE_NAME_COL).append(" LIKE '").append(middleInitial).append("%' ");				
				}
				nameAppended = true;
			}
		}else {
			//for first or middle initial: SELECT thmId FROM authorTb WHERE SUBSTR(firstName,1,1)="z";
			// use LIKE 'Start%' 
			String firstInitial = author.firstInitial();
			if(!"".equals(firstInitial)) {
				//querySb.append(" SUBSTR(").append(FIRST_NAME_COL).append(",1,1)='").append(firstInitial).append("' ");
				//LIKE can tell that starting string is asked, and can take advantage of table indexing
				querySb.append(" t").append(authorCounter).append(".").append(FIRST_NAME_COL).append(" LIKE '").append(firstInitial).append("%' ");
				nameAppended = true;				
			}
		}
		String lastName = author.lastName();
		if(nameAppended) {
			querySb.append(" AND t").append(authorCounter).append(".").append(LAST_NAME_COL).append("='").append(lastName).append("' ").append(relationStr);	
		}else {
			querySb.append(" t").append(authorCounter).append(".").append(LAST_NAME_COL).append("='").append(lastName).append("') ").append(relationStr);			
		}
		querySb.append(" ");
		//System.out.println("queryL "+querySb);
	}
	
	
}
