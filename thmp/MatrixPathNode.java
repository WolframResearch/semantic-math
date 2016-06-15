package thmp;

/**
 * This is a path through the entries of the matrix mx,
 * recording the path and score for path so far.
 * Forming a tree structure.
 * 
 * @author yihed
 */
public class MatrixPathNode {

	// 
	private MatrixPathNode leftNode;
	private MatrixPathNode rightNode;
	// the position in the matrix mx, pair of numbers
	private int[] mxPosition;
	// index in the List in each mx entry
	private int listIndex;
	double ownScore;
	// score so far along the path
	double scoreSoFar;
	
	
	public MatrixPathNode(int[] pos, int index, double ownScore, double scoreSoFar,
			MatrixPathNode leftNode, MatrixPathNode rightNode){
		this.mxPosition = pos;
		this.listIndex = index;
		this.leftNode = leftNode;
		this.rightNode = rightNode;
		this.ownScore = ownScore;
		this.scoreSoFar = scoreSoFar;
	}
	
	/**
	 * @return the leftNode
	 */
	public MatrixPathNode getLeftNode() {
		return leftNode;
	}

	/**
	 * @param leftNode the leftNode to set
	 */
	public void setLeftNode(MatrixPathNode leftNode) {
		this.leftNode = leftNode;
	}

	/**
	 * @return the rightNode
	 */
	public MatrixPathNode getRightNode() {
		return rightNode;
	}

	/**
	 * @param rightNode the rightNode to set
	 */
	public void setRightNode(MatrixPathNode rightNode) {
		this.rightNode = rightNode;
	}

	/**
	 * @return the mxPosition
	 */
	public int[] getMxPosition() {
		return mxPosition;
	}

	/**
	 * @param mxPosition the mxPosition to set
	 */
	public void setMxPosition(int[] mxPosition) {
		this.mxPosition = mxPosition;
	}

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
