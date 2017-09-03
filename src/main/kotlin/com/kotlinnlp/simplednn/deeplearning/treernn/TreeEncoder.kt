/* Copyright 2016-present The KotlinNLP Authors. All Rights Reserved.
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, you can obtain one at http://mozilla.org/MPL/2.0/.
 * ------------------------------------------------------------------*/

package com.kotlinnlp.simplednn.deeplearning.treernn

import com.kotlinnlp.simplednn.core.neuralprocessor.feedforward.FeedforwardNeuralProcessor
import com.kotlinnlp.simplednn.core.neuralprocessor.feedforward.FeedforwardNeuralProcessorsPool
import com.kotlinnlp.simplednn.core.neuralprocessor.recurrent.RecurrentNeuralProcessor
import com.kotlinnlp.simplednn.core.neuralprocessor.recurrent.RecurrentNeuralProcessorsPool
import com.kotlinnlp.simplednn.simplemath.concatVectorsV
import com.kotlinnlp.simplednn.simplemath.ndarray.dense.DenseNDArray
import com.kotlinnlp.simplednn.simplemath.ndarray.dense.DenseNDArrayFactory

/**
 * The TreeEncoder realizes an incremental compositional vector representation of tree
 * that relies on a recursive combination of recurrent-neural network encoders.
 *
 * You have to instantiate a new TreeEncoder for each tree to encode.
 * Different TreeEncoder instances could share the same [network].
 *
 * Usage:
 *
 *   To encode a tree the first operation to do is to add the terminal nodes using the function [addNode]
 *   which requires a node Id and an node vector representation. At this point each node contains
 *   its encoding representation. The function [getNode] return the node associated to a given Id.
 *   The function [setHead] create a hierarchical dependence from a node to the head-node,
 *   causing a re-encoding of the latter.
 *
 *   The objective of the encoder is to create a suitable node representation for the module that uses it.
 *   During training the loss are propagated to to the nodes which contributed to the error. For the purpose
 *   the [addEncodingErrors] function add the encoding errors to the node with the given Id.
 *   After setting the errors in a certain state of the tree-encoding process, you must propagate the errors
 *   using the [propagateErrors] function. Since each node-encoding depends on the network’s parameters,
 *   each parameter update will invalidate all the encodings. Therefore the errors of the network parameters
 *   are accumulated into an optimizer at each propagation.
 *   At the end of the process (for example when the tree is complete) you call the update function of the optimizer
 *   which finally optimize the network parameters.
 *
 * The algorithm is a free interpretation of the key concepts described in the paper below.
 *
 * Reference:
 * [Eliyahu Kiperwasser and Yoav Goldberg - Easy-First Dependency Parsing with Hierarchical Tree LSTMs](https://www.transacl.org/ojs/index.php/tacl/article/viewFile/798/208)
 *
 * @property network the TreeRNN of this encoder
 */
class TreeEncoder(private val network: TreeRNN) {

  /**
   * A Node contains the the encoded vector representation resulting by the encoding process
   * and the errors of the vector resulting from errors propagation of the learning process.
   *
   * @property id the id of the Node, range [0, n]
   * @property vector the vector corresponding to an external terminal element
   *                 (e.g. a vector corresponding to the ith word in a sentence)
   */
  inner class Node(val id: Int, val vector: DenseNDArray) {

    /**
     * The encoded vector representation of this node.
     */
    var encoding: DenseNDArray
      private set

    /**
     * The errors associated to the vector of the node.
     */
    val vectorErrors: DenseNDArray = DenseNDArrayFactory.zeros(shape = this.vector.shape)

    /**
     * Whether the node is a root.
     */
    internal var isRoot: Boolean = true
      internal set

    /**
     * The left-children nodes.
     */
    internal val leftChildren = ArrayList<Node>()

    /**
     * The right-children nodes.
     */
    internal val rightChildren = ArrayList<Node>()

    /**
     * The errors associated to the current encoding representation of this node.
     */
    internal var encodingErrors: DenseNDArray? = null

    /**
     * The [RecurrentNeuralProcessor] to encode the left-children.
     */
    internal val leftProcessor: RecurrentNeuralProcessor<DenseNDArray>
      = this@TreeEncoder.leftProcessorsPool.getItem()

    /**
     * The [RecurrentNeuralProcessor] to encode the right-children.
     */
    internal val rightProcessor: RecurrentNeuralProcessor<DenseNDArray>
      = this@TreeEncoder.rightProcessorsPool.getItem()

    /**
     * The [FeedforwardNeuralProcessor] to take two d-dimensional vectors and returning a single d-dimensional vector.
     */
    internal val concatProcessor: FeedforwardNeuralProcessor<DenseNDArray>
      = this@TreeEncoder.concatProcessorsPool.getItem()

    /**
     * The head of the node.
     */
    private var head: Node? = null

    /**
     * The errors generated by the top-down recursive back-propagation.
     */
    private var backwardErrors: DenseNDArray? = null

    /**
     * Initialize the node setting its encoding representation.
     */
    init {
      this.encoding = this.encode()
    }

    /**
     * Reset the encoding and backward errors associated to the Node.
     * Warning: the [vectorErrors] are still maintained.
     */
    internal fun resetNodeErrors() {
      this.encodingErrors = null
      this.backwardErrors = null
    }

    /**
     * Add the given [errors] to the [encodingErrors] of the Node.
     *
     * @param errors the errors to add to the [encodingErrors] array
     */
    internal fun addEncodingErrors(errors: DenseNDArray) {

      if (this.encodingErrors != null) {
        this.encodingErrors!!.assignSum(errors)
      } else {
        this.encodingErrors = errors.copy()
      }
    }

    /**
     * The errors associated to a given node can be set using the [TreeEncoder.addEncodingErrors] method or
     * automatically generated during the back-propagation process.
     * In this implementation the errors are combined by summation.
     *
     * @return the sum of the errors associated to the Node or null if it has no errors associated to it
     */
    internal fun getNodeErrors(): DenseNDArray? = when {
      this.encodingErrors != null && this.backwardErrors != null -> this.encodingErrors!!.sum(this.backwardErrors!!)
      this.encodingErrors != null -> this.encodingErrors
      this.backwardErrors != null -> this.backwardErrors
      else -> null
    }

    /**
     * Append the [child] to the left or right children of this node respect their the linear order.
     * Set the child as non-root and the head of the child at the current node.
     *
     * Set the [encoding] property of this node.
     *
     * @param child a child of the this node
     */
    internal fun addChild(child: Node) {

      child.isRoot = false
      child.head = this

      if (child.id < this.id) {
        this.leftChildren.add(child)
      } else {
        this.rightChildren.add(child)
      }

      this.encoding = this.encode()
      
      this.encodeHead()
    }

    /**
     * Encode the node.
     *
     * The node encoding representation is a concatenation of the RNN encoding
     * of the left-children with the RNN encoding of the right-children.
     * The concatenation is reduced back to vector-dimensions using a linear transformation
     * followed by a non-linear activation function.
     *
     * @return the encoding representation
     */
    internal fun encode(): DenseNDArray = this.concatProcessor.forward(concatVectorsV(
      this.encodeChildren(processor = this.leftProcessor, head = this.vector, children = this.leftChildren),
      this.encodeChildren(processor = this.rightProcessor, head = this.vector, children = this.rightChildren)))

    /**
     * Propagate errors to the descendants using the back-propagation algorithm.
     */
    internal fun propagateErrors() {

      val errors: DenseNDArray? = this.getNodeErrors()

      if (errors != null && (0 until errors.length).any { errors[it] != 0.0 }) {
        this.backward(errors)
      }

      this.leftChildren.forEach { it.propagateErrors() }
      this.rightChildren.forEach { it.propagateErrors() }
    }

    /**
     * Propagate errors to this node and its children.
     *
     * This function is the "backward" of the [encode] function.
     *
     * @param errors the errors accumulated on this node to propagate
     */
    private fun backward(errors: DenseNDArray) {

      val (leftErrors, rightErrors) = this.backwardConcat(errors)

      this.backwardChildren(processor = this.leftProcessor, children = this.leftChildren, errors = leftErrors)
      this.backwardChildren(processor = this.rightProcessor, children = this.rightChildren, errors = rightErrors)
    }

    /**
     * Propagate the errors to the throughout the [concatProcessor].
     *
     * @param errors the errors to propagate
     *
     * @return pair of left and right errors
     */
    private fun backwardConcat(errors: DenseNDArray): Pair<DenseNDArray, DenseNDArray> {

      this.concatProcessor.backward(errors, propagateToInput = true)

      return this.splitLeftAndRightErrors(this.concatProcessor.getInputErrors(copy = false))
    }

    /**
     * Encode the sequence of children from the head outwards.
     * The first input to each is the vector representation of the head node word,
     * and the last input is the vector representation of the last added child.
     *
     * @param processor left or right recurrent neural processor
     * @param head the node vector
     * @param children left or right children nodes.
     *                 NOTE: The children direction must be aligned with the one of the processor.
     */
    private fun encodeChildren(processor: RecurrentNeuralProcessor<DenseNDArray>,
                               head: DenseNDArray,
                               children: ArrayList<Node>): DenseNDArray {

      processor.forward(head, firstState = true)

      children.forEach { processor.forward(it.encoding, firstState = false) }

      return processor.getOutput(copy = false)
    }

    /**
     * Propagate the errors to the vector and the children.
     *
     * This method is the "backward" of the [encodeChildren] function.
     */
    private fun backwardChildren(processor: RecurrentNeuralProcessor<DenseNDArray>,
                                 children: ArrayList<Node>,
                                 errors: DenseNDArray) {

      processor.backward(errors, propagateToInput = true)

      val sequenceErrors = processor.getInputSequenceErrors()

      this.vectorErrors.assignSum(sequenceErrors[0])

      children.forEachIndexed { i, node -> node.backwardErrors = sequenceErrors[i + 1] } // skip head
    }

    /**
     * Split [errors] in left and right errors.
     *
     * @param errors errors to split in left and right errors
     *
     * @return a (leftErrors, rightErrors) Pair
     */
    private fun splitLeftAndRightErrors(errors: DenseNDArray) = Pair(
      errors.getRange(0, errors.length / 2),
      errors.getRange(errors.length / 2, errors.length))

    /**
     * When a child is added to a node that has a head,
     * the head must be recursively re-encoded until the root.
     */
    private fun encodeHead() {
      val head = this.head

      if (head != null) {
        head.encoding = head.encode()
        head.encodeHead()
      }
    }
  }

  /**
   * The pool of processors for the left RNN.
   */
  private val leftProcessorsPool = RecurrentNeuralProcessorsPool<DenseNDArray>(this.network.leftRNN)

  /**
   * The pool of processors for the right RNN.
   */
  private val rightProcessorsPool = RecurrentNeuralProcessorsPool<DenseNDArray>(this.network.rightRNN)

  /**
   * The pool of processors for the concat output network.
   */
  private val concatProcessorsPool = FeedforwardNeuralProcessorsPool<DenseNDArray>(this.network.concatNetwork)

  /**
   * The nodes mapped to their IDs.
   */
  private val nodes = mutableMapOf<Int, Node>()

  /**
   * A list of nodes containing encoding errors.
   */
  private val nodesWithEncodingErrors = mutableSetOf<Node>()

  /**
   * Add a new node to the TreeEncoder.
   *
   * @param id the id of the new node
   * @param vector the values of the new node
   *
   * @return the new added node
   */
  fun addNode(id: Int, vector: DenseNDArray): Node {
    require(id !in this.nodes) { "Node $id already inserted" }

    this.nodes[id] = Node(id = id, vector = vector)

    return this.nodes[id]!!
  }

  /**
   * Get the Node given its id.
   *
   * @param id the id a Node. The id must be included in the nodes of this encoder
   * @return the Node associated with the [id]
   */
  fun getNode(id: Int): Node {
    require(id in this.nodes) { "Node $id not found" }
    return this.nodes[id]!!
  }

  /**
   * Set the head of a given node and (re)encode the head node.
   *
   * @param nodeId the id of the child node (the id must be in the nodes list)
   * @param headId the id of the head head (the id must be in the nodes list)
   */
  fun setHead(nodeId: Int, headId: Int) {
    require(nodeId != headId) { "Cannot set node $nodeId as head of itself" }
    require(nodeId in this.nodes) { "Node $nodeId not found" }
    require(headId in this.nodes) { "Head node $headId not found" }

    val head = this.nodes[headId]!!
    val node = this.nodes[nodeId]!!

    head.addChild(node)
  }

  /**
   * Assign the encoding errors to the node given its ID.
   *
   * @param nodeId the Id of the node to which assign the errors
   *               The node must be a root
   * @param errors encoding errors to assign to the node [nodeId].
   *               The errors must have the same size of the vector.
   */
  fun addEncodingErrors(nodeId: Int, errors: DenseNDArray) {

    require(nodeId in this.nodes) { "Node $nodeId not found" }

    val node = this.nodes[nodeId]!!

    require(errors.shape == node.vector.shape) {
      "Errors size (%d) not compatible with vector size (%d)".format(errors.length, node.vector.length)
    }

    node.addEncodingErrors(errors)

    this.nodesWithEncodingErrors.add(node)
  }

  /**
   * @return the list of ids of the root nodes
   */
  fun getRootsIds(): List<Int> = this.nodes.values.filter({ it.isRoot }).map({ it.id })

  /**
   * Propagate the encoding errors starting from the nodes with encoding errors.
   * Accumulate the resulting network parameters errors.
   * Clear the errors of the nodes.
   *
   * @param optimizer the optimizer in which to accumulate the errors of the parameters of the [TreeRNN]
   */
  fun propagateErrors(optimizer: TreeRNNOptimizer) {

    this.nodes.values.filter({ it.isRoot }).forEach { this.launchErrorsPropagation(it) }
    this.accumulateParamsErrors(optimizer)
    this.clearNodeErrors()
  }

  /**
   * Reset the Tree removing all nodes.
   */
  fun clearTree() {

    this.nodes.clear()
    this.nodesWithEncodingErrors.clear()

    this.leftProcessorsPool.releaseAll()
    this.rightProcessorsPool.releaseAll()
    this.concatProcessorsPool.releaseAll()
  }

  /**
   * Launch the propagation starting from the given [node] if it contains encoding errors, recur to children otherwise.
   *
   * @param node the node from which to start the propagation of the errors
   */
  private fun launchErrorsPropagation(node: Node) {

    if (node in this.nodesWithEncodingErrors) {
      node.propagateErrors()

    } else {
      node.leftChildren.forEach { this.launchErrorsPropagation(it) }
      node.rightChildren.forEach { this.launchErrorsPropagation(it) }
    }
  }

  /**
   * Accumulate the parameters errors calculated during the recursive back-propagation into the optimizer.
   *
   * @param optimizer the optimizer in which to accumulate the errors
   */
  private fun accumulateParamsErrors(optimizer: TreeRNNOptimizer) {

    optimizer.newBatch()

    this.nodes.values.forEach {
      val errors: DenseNDArray? = it.getNodeErrors()

      if (errors != null && (0 until errors.length).any { errors[it] != 0.0 }) { // optimization
        optimizer.newExample()

        optimizer.accumulate(TreeRNNParameters(
            leftRNN = it.leftProcessor.getParamsErrors(copy = false),
            rightRNN = it.rightProcessor.getParamsErrors(copy = false),
            concatNetwork = it.concatProcessor.getParamsErrors(copy = false)))
      }
    }
  }

  /**
   * Clear the errors of the nodes except the vector errors which are still maintained.
   */
  private fun clearNodeErrors() {
    this.nodes.values.forEach { it.resetNodeErrors() }
    this.nodesWithEncodingErrors.clear()
  }
}