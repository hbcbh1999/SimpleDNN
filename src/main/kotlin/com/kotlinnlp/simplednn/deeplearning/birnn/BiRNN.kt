/* Copyright 2016-present The KotlinNLP Authors. All Rights Reserved.
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, you can obtain one at http://mozilla.org/MPL/2.0/.
 * ------------------------------------------------------------------*/

package com.kotlinnlp.simplednn.deeplearning.birnn

import com.kotlinnlp.simplednn.core.functionalities.activations.ActivationFunction
import com.kotlinnlp.simplednn.core.functionalities.initializers.GlorotInitializer
import com.kotlinnlp.simplednn.core.functionalities.initializers.Initializer
import com.kotlinnlp.simplednn.core.layers.LayerConfiguration
import com.kotlinnlp.simplednn.core.layers.LayerType
import com.kotlinnlp.simplednn.core.neuralnetwork.NeuralNetwork
import com.kotlinnlp.simplednn.utils.Serializer
import java.io.InputStream
import java.io.OutputStream
import java.io.Serializable

/**
 * Bidirectional Recursive Neural Network (BiRNN).
 *
 * The class contains the sub-networks which constitute a BiRNN.
 *
 *   Reference:
 *   Mike Schuster and Kuldip K. Paliwal. - Bidirectional recurrent neural networks
 *
 * @property inputType the type of the input layer (Dense, Sparse, SparseBinary)
 * @property inputSize the size of the input layer of each RNN
 * @property hiddenSize the size of the output layer of each RNN
 * @property hiddenActivation the activation function of the output layer
 * @property recurrentConnectionType type of recurrent neural network (e.g. LSTM, GRU, CFN, SimpleRNN)
 * @property dropout the probability of dropout (default 0.0). If applying it, the usual value is 0.25.
 * @property outputSize the size of the [BiRNN] output layer results from the concatenation
 *                      of the hidden layers of each RNN
 * @param weightsInitializer the initializer of the weights (zeros if null, default: Glorot)
 * @param biasesInitializer the initializer of the biases (zeros if null, default: Glorot)
 */
class BiRNN(
  val inputType: LayerType.Input,
  val inputSize: Int,
  val hiddenSize: Int,
  val hiddenActivation: ActivationFunction?,
  val dropout: Double = 0.0,
  val recurrentConnectionType: LayerType.Connection,
  weightsInitializer: Initializer? = GlorotInitializer(),
  biasesInitializer: Initializer? = GlorotInitializer()) : Serializable {

  companion object {

    /**
     * Private val used to serialize the class (needed from Serializable)
     */
    @Suppress("unused")
    private const val serialVersionUID: Long = 1L

    /**
     * Read a [BiRNN] (serialized) from an input stream and decode it.
     *
     * @param inputStream the [InputStream] from which to read the serialized [BiRNN]
     *
     * @return the [BiRNN] read from [inputStream] and decoded
     */
    fun load(inputStream: InputStream): BiRNN = Serializer.deserialize(inputStream)
  }

  /**
   * The size of the output layer resulting from the concatenation of the hidden layers of the [leftToRightNetwork] and
   * [rightToLeftNetwork].
   */
  val outputSize: Int = this.hiddenSize * 2

  /**
   * The Recurrent Neural Network to process the sequence left-to-right.
   */
  val leftToRightNetwork = NeuralNetwork(
    LayerConfiguration(
      size = this.inputSize,
      inputType = this.inputType,
      dropout = dropout),
    LayerConfiguration(
      size = this.hiddenSize,
      activationFunction = this.hiddenActivation,
      connectionType = this.recurrentConnectionType
    ),
    weightsInitializer = weightsInitializer,
    biasesInitializer = biasesInitializer
  )

  /**
   * The Recurrent Neural Network to process the sequence right-to-left.
   */
  val rightToLeftNetwork = NeuralNetwork(
    LayerConfiguration(
      size = this.inputSize,
      inputType = this.inputType,
      dropout = dropout),
    LayerConfiguration(
      size = this.hiddenSize,
      activationFunction = this.hiddenActivation,
      connectionType = this.recurrentConnectionType
    ),
    weightsInitializer = weightsInitializer,
    biasesInitializer = biasesInitializer
  )

  /**
   * The model of this [BiRNN] containing its parameters.
   */
  val model = BiRNNParameters(leftToRight = this.leftToRightNetwork.model, rightToLeft = this.rightToLeftNetwork.model)

  /**
   * Check connection to the output layer.
   */
  init {
    require(this.recurrentConnectionType.property == LayerType.Property.Recurrent) {
      "required recurrentConnectionType with Recurrent property"
    }
  }

  /**
   * Serialize this [BiRNN] and write it to an output stream.
   *
   * @param outputStream the [OutputStream] in which to write this serialized [BiRNN]
   */
  fun dump(outputStream: OutputStream) = Serializer.serialize(this, outputStream)
}
