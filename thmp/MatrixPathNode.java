package thmp;

/**
 * This is a path through the entries of the matrix mx,
 * recording the path and score for path so far.
 * Forming a tree structure.
 * 
 * @author yihed
 */
public class MatrixPathNode {

	// reference back to Struct to get the two chilren nodes
	//private MatrixPathNode leftNode;
	//private MatrixPathNode rightNode;
	private Struct curStruct;
	//private StructList structList;
	// the position in the matrix mx, pair of numbers
	//don't need mxPosition
	//private int[] mxPosition;
	
	// index of struct in the structList in each mx entry
	// don't really need this yet, will keep til later
	private int listIndex;
	//should get this from curStruct!
	private double ownScore;
	// score so far along the path
	// don't need this, it's computed along in the DFS
	private double scoreSoFar;
	
	//must set leftNode and rightNode, and structList later
	/*public MatrixPathNode(int index, double ownScore, double scoreSoFar,
			Struct curStruct
			){
		this.listIndex = index;
		this.ownScore = ownScore;
		this.scoreSoFar = scoreSoFar;
		this.curStruct = curStruct;
	} */
	
	/**
	 * 
	 * @param index
	 * @param ownScore
	 * @param scoreSoFar
	 * @param leftNode
	 * @param rightNode
	 * @param curStruct is Struct corresponding to this mxEntry
	 * @param structList is List of Struct's in the mx entry
	 */
	public MatrixPathNode(int index, double ownScore,
			Struct curStruct){
		//this.mxPosition = pos;
		this.listIndex = index;
		//this.leftNode = leftNode;
		//this.rightNode = rightNode;
		this.ownScore = ownScore;
		//this.scoreSoFar = scoreSoFar;
		this.curStruct = curStruct;
		//this.structList = structList;
	}
	
	public MatrixPathNode(double ownScore,
			Struct curStruct){
		//this.mxPosition = pos;
		//this.leftNode = leftNode;
		//this.rightNode = rightNode;
		this.ownScore = ownScore;
		//this.scoreSoFar = scoreSoFar;
		this.curStruct = curStruct;
		//this.structList = structList;
	}
	
	/*public StructList structList() {
		return structList;
	} */
	
	public double scoreSoFar() {
		return scoreSoFar;
	}
	
	public Struct curStruct() {
		return curStruct;
	}
	
	/**
	 * @return the leftNode
	 */
	/*public MatrixPathNode getLeftNode() {
		return leftNode;
	} */

	/**
	 * @param leftNode the leftNode to set
	 */
	/*public void setLeftNode(MatrixPathNode leftNode) {
		this.leftNode = leftNode;
	} */

	/**
	 * @return the rightNode
	 */
	/*
	public MatrixPathNode getRightNode() {
		return rightNode;
	}

	/**
	 * @param rightNode the rightNode to set
	 
	public void setRightNode(MatrixPathNode rightNode) {
		this.rightNode = rightNode;
	} */

	/**
	 * @return the mxPosition
	 *//*
	public int[] getMxPosition() {
		return mxPosition;
	} */

	/**
	 * @param mxPosition the mxPosition to set
	 */ /*
	public void setMxPosition(int[] mxPosition) {
		this.mxPosition = mxPosition;
	} */

	/**
	 * @return the listIndex
	 */
	public int getListIndex() {
		return listIndex;
	}

	/**
	 * @param listIndex the listIndex to set
	 */
	public void setListIndex(int listIndex) {
		this.listIndex = listIndex;
	}

	/**
	 * @return the ownScore
	 */
	public double getOwnScore() {
		return ownScore;
	}

	/**
	 * @param ownScore the ownScore to set
	 */
	public void setOwnScore(double ownScore) {
		this.ownScore = ownScore;
	}

	/**
	 * @return the scoreSoFar
	 */
	public double getScoreSoFar() {
		return scoreSoFar;
	}

	/**
	 * @param scoreSoFar the scoreSoFar to set
	 */
	public void setScoreSoFar(double scoreSoFar) {
		this.scoreSoFar = scoreSoFar;
	}

	
	
}
