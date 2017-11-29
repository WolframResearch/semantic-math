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
import thmp.utils.DBUtils.ConjDisjType;
import thmp.utils.WordForms;
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
		
		private AuthorRelationNode rootNode;
		private String sqlQueryStr; 
		//names to set in the prepared statement.
		private List<String> namesToSet;
		/**used for constructing JOIN's*/
		private int numAuthors;
		
		/**
		 * @param userInput e.g. (A. Author1 and (B. Author2 or C Author3)). 
		 */
		public AuthorRelation(String userInput) {
			
			this.rootNode = parseAuthorStr(userInput);
			this.numAuthors = this.rootNode.numAuthors;
			System.out.println("rootNote "+this.rootNode);
			
			int curLevelNameListSz = this.rootNode.childrenList.size();
			int typeListSz = this.rootNode.childRelationList.size();
			if(curLevelNameListSz != typeListSz+1) {
				System.out.println("curLevelNameList " +this.rootNode.childrenList);
				System.out.println("typeList " +this.rootNode.childRelationList);
				throw new IllegalArgumentException("Mismatched authors and conj/disj operators: curLevelNameListSz, typeListSz+1: "
						+ curLevelNameListSz + ", " + (typeListSz+1));
			}
			boolean abbreviateNameBool = false;
			boolean abbreviateLastNameBool = false;
			this.namesToSet = new ArrayList<String>();
			
			StringBuilder querySb = generateQueryStr(this.rootNode, this.namesToSet, abbreviateNameBool, abbreviateLastNameBool);
			
			//produce list of arguments, to set, e.g. pstmt.setInt(2, 110592)
			//all Strings.
			this.sqlQueryStr = querySb.toString();
			
			logger.info("searching author - query created: "+this.sqlQueryStr);
		}

		/**
		 * Create query String for given node. 
		 * @param node
		 * @param namesToSet
		 * @param abbreviateNameBool
		 * @return
		 */
		private static StringBuilder generateQueryStr(AuthorRelationNode node, List<String> namesToSet, 
				boolean abbreviateNameBool, boolean abbreviateLastNameBool) {
			//StringBuilder sqlSb = new StringBuilder(300);
			//SELECT t1.thmId FROM (authorTb AS t1,authorTb AS t2) WHERE t1.author='author2' AND t2.author='author1';
			////SELECT t0.thmId FROM (authorTb AS t0, authorTb AS t1) WHERE ( t0.thmId=t1.thmId AND t0.firstName LIKE 't%'  
			//AND t0.lastName='tao' AND  t1.firstName LIKE 'y%'  AND t1.lastName='shalom' );
			StringBuilder querySb = new StringBuilder("SELECT t0.").append(THM_ID_COL).append(" FROM (");		
			int numAuthors = node.numAuthors;
			
			for(int i = 0; i < numAuthors; i++) {
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
			
			if(numAuthors > 1) {
				for(int i = 0; i < numAuthors-1; i++) {
					querySb.append("t")
					.append(i)
					.append(".")
					.append(THM_ID_COL)
					.append("=");
				}
				querySb.append("t").append(numAuthors-1)
				.append(".")
				.append(THM_ID_COL)
				.append(" AND ");
			}
			
			int authorCounter = 0;
			
			//build sql query str, e.g. con.prepareStatement("UPDATE EMPLOYEES
            //SET SALARY = ? WHERE ID = ?");
			
			//+1 because the counter starts at 0 for the first instance, instead of 1.
			int builtAuthorCounter = buildSqlStr(node, querySb, authorCounter, namesToSet, 
					abbreviateNameBool, abbreviateLastNameBool) + 1;
			
			querySb.append(";");			
			 
			if(builtAuthorCounter != node.numAuthors) {
				String queryStr = querySb.toString();
				System.out.println("Parsing error: inconsistent authorCounters! " + builtAuthorCounter + " "+ queryStr);
				logger.error("Parsing error: inconsistent authorCounters! Query " + queryStr);
			}
			return querySb;
		}
		
		/**
		 * Builds sql query string.
		 * @param rootNode2
		 * @param sqlSb
		 * @param authorCounter used for JOIN's
		 * @param list of names to set.
		 * @param abbreviateNameBool whether to abbreviate (first/last)name to initials.
		 * @return updated authorCounter
		 */
		private static int buildSqlStr(AuthorRelationNode node, StringBuilder sqlSb, int authorCounter,
				List<String> namesToSet, boolean abbreviateNameBool, boolean abbreviateLastNameBool) {
			
			sqlSb.append("(");
			List<AuthorRelationNode> childrenList = node.childrenList;
			List<ConjDisjType> typeList = node.childRelationList;
			
			int childrenListSz = childrenList.size();
			int typeListSz = typeList.size();
			
			for(int i = 0; i < typeListSz; i++) {
				AuthorRelationNode curNode = childrenList.get(i);
				ConjDisjType type = typeList.get(i);
				String typeStr = type.getDbName();
				if(curNode.hasChild()) {					
					authorCounter = buildSqlStr(curNode, sqlSb, authorCounter, namesToSet, abbreviateNameBool,
							abbreviateLastNameBool);
					sqlSb.append(" ").append(typeStr).append(" ");
					
				}else {
					/*
					 createAuthorQuery(String relationStr, StringBuilder querySb, AuthorName author,
			boolean abbreviateNameBool)
					 */
					createAuthorQuery(typeStr, sqlSb, curNode.authorName, authorCounter, namesToSet, 
							abbreviateNameBool, abbreviateLastNameBool);					
				}
				//increment counter if conj, regardless of whether same level or nested child
				if(ConjDisjType.CONJ == type) {
					authorCounter++;
				}
			}
			AuthorRelationNode lastNode = childrenList.get(childrenListSz-1);
			if(lastNode.hasChild()) {
				authorCounter = buildSqlStr(lastNode, sqlSb, authorCounter, namesToSet, 
						abbreviateNameBool, abbreviateLastNameBool);
			}else {
				//dummy OR
				createAuthorQuery("OR", sqlSb, lastNode.authorName, authorCounter, namesToSet, abbreviateNameBool,
						abbreviateLastNameBool);
				//authorCounter++;
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
			
			StringBuilder updatedSb = new StringBuilder(200);
			/*First sanity check such as ensuring parentheses matching, 
			 * try to salvage if possible*/
			boolean modified = matchParentheses(str, updatedSb);
			if(modified) {
				//System.out.println("Input string modified!");
				//logger.info("Input query string modified by sql query parser!");
				
				//set some flag
				//communicate it to user!!				
			}
			str = updatedSb.toString();
			
			AuthorRelationNode rootNode = new AuthorRelationNode( );
			int strLen = str.length();
			int index = 0;
			while(index < strLen && str.charAt(index) == ' ') {
				index++;
			}
			//add first author
			rootNode.numAuthors = 1;
			parseAuthorStr(str, index, rootNode);
			return rootNode;
		}
		
		/**
		 * Check parentheses matching, remove extra ')' when detected,
		 * add matching number of ')' at end if insufficient number of ')'.
		 * 
		 * @param str input string.
		 * @param sb StringBuilder to fill with updated string.
		 * @return whether the input string needs to be modified to produce syntactically correct str.
		 * @throws IllegalArgumentException if unsalvageable matching.
		 */
		private static boolean matchParentheses(String str, StringBuilder sb) {
			
			int openParenCount = 0; 
			int closeParenCount = 0; 
			int strLen = str.length();
			//StringBuilder sb = new StringBuilder(50);
			//whether had to modify string, e.g. patch up parentheses
			boolean modified = false;
			
			for(int i = 0; i < strLen; i++) {
				char curChar = str.charAt(i);
				if(curChar == '(') {
					openParenCount++;
					sb.append(curChar);
				}else if(curChar == ')') {
					if(closeParenCount < openParenCount) {
						closeParenCount++;
						sb.append(curChar);
					}else {
						//raise some flag to notify user!		
						modified = true;
					}					
				}else {
					sb.append(curChar);
				}				
			}
			if(closeParenCount < openParenCount) {
				modified = true;
				sb.append(')');
				closeParenCount++;
				while(closeParenCount < openParenCount) {
					sb.append(')');
					closeParenCount++;
				}
			}
			/*can't happen any more
			 * if(openParenCount != closeParenCount) {
				throw new IllegalArgumentException("Parentheses don't match!");
			}*/
			return modified;
		}
		
		/**
		 * Make prepared statement for search-by-author query, 
		 * Sets list of names in the sqlQueryStr.
		 * @param conn
		 * @throws SQLException 
		 */
		public PreparedStatement getPreparedStm(Connection conn, boolean abbreviateNameBool,
				boolean abbreviateLastNameBool) throws SQLException {
			PreparedStatement pstm = null;
			List<String> names;
			try {
				if(!abbreviateNameBool) {
					pstm = conn.prepareStatement(this.sqlQueryStr);
					names = this.namesToSet;
				}else {
					//here
					names = new ArrayList<String>();
					StringBuilder querySb = generateQueryStr(this.rootNode, names, abbreviateNameBool,
							abbreviateLastNameBool);
					pstm = conn.prepareStatement(querySb.toString());
				}
				int i = 1;
				for(String s : names) {
					pstm.setString(i, s);
					i++;
				}				
			} catch (SQLException e) {
				// better to just throw, catch later, and do usual intersection search.
				logger.error("SQLException when acquiring prepared statement!");
				throw e;
			}
			
			return pstm;
		}
		
		public String sqlQueryStr() {
			return this.sqlQueryStr;
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
			//List<String> curLevelNameList = new ArrayList<String>();
			//List<ConjDisjType> curLevelTypeList = new ArrayList<ConjDisjType>();
			
			//Used to determine if operator e.g. AND OR expected next
			SqlParseState parseState = SqlParseState.STR;
			//operator e.g. AND OR expected next
			//boolean opExpected = false;
			//cannot be operator following this
			//boolean strExpected = false;
			
			//append to parent node according to parse
			while(index < strLen) {
				char curChar = str.charAt(index);
				if(index > 0) {
					boolean nameEnded = false;
					if(index + 4 < strLen && (str.substring(index-1, index+4).equals(" and ") )) {
						
						nameEnded = true;
						//take care of e.g. "and and"
						//curLevelTypeList.add(ConjDisjType.CONJ);
						parentNode.addChildConjDisjType(ConjDisjType.CONJ);
						index += 4;
						//only increment author count on AND
						parentNode.numAuthors++;
						//conjDisjType = ConjDisjType.CONJ;
					}else if (index + 3 < strLen && str.substring(index-1, index+3).equals(" or ")){
						
						//curLevelTypeList.add(ConjDisjType.DISJ);
						parentNode.addChildConjDisjType(ConjDisjType.DISJ);
						nameEnded = true;
						index += 3;
						//conjDisjType = ConjDisjType.DISJ;
					}
					if(nameEnded) {						
						if(//nameSb.length() == 0 || 
								parseState == SqlParseState.STR
								/*strExpected /*e.g. "and and" */) {
							//curLevelTypeList.remove(curLevelTypeList.size()-1);
							parentNode.childRelationList.remove(parentNode.childRelationList.size()-1);
						}else if(nameSb.length() > 0)
						{
							//curLevelNameList.add(nameSb.toString());
							parentNode.addChildNode(new AuthorRelationNode(nameSb.toString()));
							
						}
						nameSb = new StringBuilder(30);
						//strExpected = true;
						//opExpected = false;
						parseState = SqlParseState.STR;
						
						while(index < strLen && str.charAt(index) == ' ') {
							index++;
						}
						continue;
					}
				}
				
				curChar = str.charAt(index);
				if(curChar == '(') {
					//take care if ( doens't follow and/or !!!
					//e.g. A and ( B and D ) C
					if(nameSb.length() > 0) {
						//interpret as OR, dummy placement
						parentNode.addChildConjDisjType(ConjDisjType.DISJ);
						parentNode.addChildNode(new AuthorRelationNode(nameSb.toString()));
						//parentNode.numAuthors++;
						nameSb = new StringBuilder(30);
					}					
					
					//parse sb and append children of current level
					//parseAndAppendChildren(curLevelNameList, curLevelTypeList, parentNode);
					AuthorRelationNode childNode = new AuthorRelationNode();
					index = parseAuthorStr(str, index+1, childNode) + 1;	
					//returned from level down
					//opExpected = true;
					parseState = SqlParseState.OP;
					
					while(index < strLen && str.charAt(index) == ' ') {
						index++;
					}
					
					parentNode.addChildNode(childNode);
					parentNode.numAuthors += childNode.numAuthors;
				}//or is switching conj disj type
				else if(curChar == ')') {
					//be careful to not always return!
					//index++;
					//curLevelNameList.add(nameSb.toString());
					//note this could cause double nesting if specified in (  )
					parentNode.addChildNode(new AuthorRelationNode(nameSb.toString()));
					//parentNode.numAuthors++;
					return ++index;
				}
				else {
					if(parseState == SqlParseState.OP
							//opExpected
							) {
						//space was already whisked away after operator
						//assume OR
						parentNode.addChildConjDisjType(ConjDisjType.DISJ);
						//opExpected = false;						
					}
					parseState = SqlParseState.EITHER;
					//strExpected = false;
					nameSb.append(curChar);
					index++;
				}			
			}
			String nameStr;
			if(nameSb.length() > 0 && 
					!(WordForms.getWhiteNonEmptySpaceNotAllPattern().matcher((nameStr=nameSb.toString())).matches()) ) {
				//curLevelNameList.add(nameSb.toString());
				parentNode.addChildNode(new AuthorRelationNode(nameStr));
				//parentNode.numAuthors++;
			}
			return index;
		}
		
	}/*end of AuthorRelation class*/
	
	/**
	 * Current parse state, e.g. whether should expect String or operator next.
	 */
	private enum SqlParseState{
		//operator e.g. AND OR expected next
		OP,
		//cannot be operator, need String
		STR,
		EITHER;				
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
		
		AuthorRelationNode() {
		}
		@Override
		public String toString() {
			return authorName + " " + childrenList + " " + childRelationList;
		}
		/**
		 * For leaf node.
		 * @param authorName
		 */
		AuthorRelationNode(String authorName) {
			this.authorName = new AuthorName(authorName);
		}
		
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
		
		//data need to be normalized, e.g. M. L. Mangano
		
		//e.g. "SELECT thmId FROM authorTb3 WHERE (author='W. N. Kang' OR author='S. Ivanov');"
		//make DB call, get default connection
		//SELECT t0.thmId FROM (authorTb AS t0, authorTb AS t1) WHERE ( t0.thmId=t1.thmId AND t0.firstName LIKE 't%'  
		//AND t0.lastName='tao' AND  t1.firstName LIKE 'y%'  AND t1.lastName='shalom' );
		Connection conn = DBUtils.getPooledConnection();
		boolean abbreviateFirstName = false;
		boolean abbreviateLastName = false;
		List<Integer> dbList = queryWithAuthors(authorRelation, conn, abbreviateFirstName, abbreviateLastName);
		//If still no hits, gradually reduce down the number of names tried.
		if(dbList.isEmpty()) {
			//replace author list with just first initials, and query again
			/*List<AuthorName> authorList2 = new ArrayList<AuthorName>();
			for(AuthorName authorName : authorList) {
				authorList2.add(authorName.abbreviateName());
			}*/
			abbreviateFirstName = true;
			dbList = queryWithAuthors(authorRelation, conn, abbreviateFirstName, abbreviateLastName);
		}
		//make into constant 
		if(dbList.isEmpty() && authorRelation.numAuthors < 3) {
			//in case of typo in last name.
			abbreviateFirstName = false;
			abbreviateLastName = true;
			dbList = queryWithAuthors(authorRelation, conn, abbreviateFirstName, abbreviateLastName);
		}
		
		DBUtils.closePooledConnection(conn);
		return dbList;
	}

	
	/*private static List<Integer> queryWithAuthors0(List<AuthorName> authorList,  String relationStr,
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
	}*/

	private static List<Integer> queryWithAuthors(AuthorRelation authorRelation,
			Connection conn, boolean abbreviateFirstNameBool, boolean abbreviateLastNameBool) throws SQLException {
		
		List<Integer> dbList = new ArrayList<Integer>();
		//StringBuilder querySb = new StringBuilder(300);
		/*StringBuilder querySb = new StringBuilder("SELECT ").append(THM_ID_COL).append(" FROM " + AUTHOR_TABLE_NAME 
				+ " WHERE "); ******/
		//decide between first and last author names
		
		//first try all of first name, if no result, try initial 
		//make separate db calls for each author
		
		PreparedStatement stm = authorRelation.getPreparedStm(conn, abbreviateFirstNameBool, abbreviateLastNameBool);
		//logger.info("searching author - stm created: "+stm);
		
		ResultSet rs = stm.executeQuery();
		
		while(rs.next()) {
			int thmId = rs.getInt(1);
			//thmId col first column
			//System.out.println("thmId "+thmId);
			dbList.add(thmId);
		}
		//Free up resources tied to statement. This also closes resultset.
		stm.close();
		rs.close();
		return dbList;
	}
	/**
	 * Create author query given an AuthorName.
	 * @param relationStr
	 * @param querySb
	 * @param author
	 */
	private static void createAuthorQuery(String relationStr, StringBuilder querySb, AuthorName author,
			int authorCounter, List<String> namesToSet, boolean abbreviateNameBool, boolean abbreviateLastNameBool) {
		
		boolean nameAppended = false;
		String firstName = author.firstName();
		if(!abbreviateNameBool && firstName.length() > 1) {
			if(!"".equals(firstName) ) {
				querySb.append(" (t").append(authorCounter).append(".").append(FIRST_NAME_COL)
				.append("= ? ");
				namesToSet.add(firstName);
				nameAppended = true;
			}
			String middleInitial = author.middleInitial();
			if(!"".equals(middleInitial) ) {
				if(nameAppended) {
					//querySb.append(" AND ").append(MIDDLE_NAME_COL).append("='").append(middleInitial).append("' ");	
					querySb.append(" AND t");
				}else {
					querySb.append(" t");				
				}
				querySb.append(authorCounter).append(".").append(MIDDLE_NAME_COL).append(" LIKE ? ");
				namesToSet.add(middleInitial + "%");
				nameAppended = true;
			}
		}else {
			//for first or middle initial: SELECT thmId FROM authorTb WHERE SUBSTR(firstName,1,1)="z";
			// use LIKE 'Start%' 
			String firstInitial = author.firstInitial();
			if(!"".equals(firstInitial)) {
				//querySb.append(" SUBSTR(").append(FIRST_NAME_COL).append(",1,1)='").append(firstInitial).append("' ");
				//LIKE can tell that starting string is asked, and can take advantage of table indexing
				querySb.append(" t").append(authorCounter).append(".").append(FIRST_NAME_COL).append(" LIKE ? ");//.append(firstInitial).append("%' ");
				namesToSet.add(firstInitial + "%");
				nameAppended = true;				
			}
		}
		
		if(nameAppended) {
			querySb.append(" AND t");
		}else {
			querySb.append(" t");			
		}
		
		String lastName = author.lastName();
		if(abbreviateLastNameBool || lastName.length() == 1) {
			String lastNameInitial = author.lastInitial();	
			querySb.append(authorCounter).append(".").append(LAST_NAME_COL).append(" LIKE ? ");
			namesToSet.add(lastNameInitial + "%");
		}else {
			querySb.append(authorCounter).append(".").append(LAST_NAME_COL).append("= ? ");//.append(lastName).append("' ");
			namesToSet.add(lastName);
		}
		
		querySb.append(relationStr).append(" ");
		//System.out.println("queryL "+querySb);
	}
	
	
}
