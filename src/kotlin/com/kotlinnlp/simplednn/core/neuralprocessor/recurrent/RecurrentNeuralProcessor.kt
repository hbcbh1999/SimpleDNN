/* Copyright 2016-present The KotlinNLP Authors. All Rights Reserved.
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, you can obtain one at http://mozilla.org/MPL/2.0/.
 * ------------------------------------------------------------------*/

package com.kotlinnlp.simplednn.core.neuralprocessor.recurrent

import com.kotlinnlp.simplednn.core.optimizer.ParamsErrorsAccumulator
import com.kotlinnlp.simplednn.core.neuralnetwork.NetworkParameters
import com.kotlinnlp.simplednn.core.neuralnetwork.NeuralNetwork
import com.kotlinnlp.simplednn.core.neuralnetwork.recurrent.RecurrentNetworkStructure
import com.kotlinnlp.simplednn.core.neuralnetwork.recurrent.StructureContextWindow
import com.kotlinnlp.simplednn.core.neuralprocessor.NeuralProcessor
import com.kotlinnlp.simplednn.simplemath.NDArray
import com.kotlinnlp.simplednn.simplemath.ndarray.Shape

/**
 *
 * @param neuralNetwork neuralNetwork
 */
class RecurrentNeuralProcessor(override val neuralNetwork: NeuralNetwork) :
  StructureContextWindow,
  NeuralProcessor {

  /**
   * sequence
   */
  private val sequence: NNSequence = NNSequence(neuralNetwork)

  /**
   * Set each time a single forward or a single backward are called
   */
  private var curStateIndex: Int = 0

  /**
   * The errors of the network model parameters calculated during a single backward
   */
  private var backwardParamsErrors: NetworkParameters = NetworkParameters(this.neuralNetwork.layersConfiguration)

  /**
   *
   */
  private var paramsErrorsAccumulator: ParamsErrorsAccumulator = ParamsErrorsAccumulator(neuralNetwork)

  /**
   *
   */
  private val zeroErrors: NDArray = NDArray.zeros(Shape(this.neuralNetwork.layersConfiguration.last().size))

  /**
   *
   */
  override fun getPrevStateStructure(): RecurrentNetworkStructure? {
    return this.sequence.getStateStructure(this.curStateIndex - 1)
  }

  /**
   *
   */
  override fun getNextStateStructure(): RecurrentNetworkStructure? {
    return this.sequence.getStateStructure(this.curStateIndex + 1)
  }

  /**
   *
   */
  override fun getParamsErrors(): NetworkParameters {
    val paramsError = this.neuralNetwork.parametersFactory()
    paramsError.assignValues(this.paramsErrorsAccumulator.getParamsErrors())
    return paramsError
  }

  /**
   * reset
   */
  override fun getOutput(copy: Boolean): NDArray{
    return if (copy) {
      this.sequence.lastStructure!!.outputLayer.outputArray.values.copy()
    } else {
      this.sequence.lastStructure!!.outputLayer.outputArray.values
    }
  }

  /**
   *
   */
  private fun addNewState() {

    val structure = RecurrentNetworkStructure(
      layersConfiguration = this.neuralNetwork.layersConfiguration,
      params = this.neuralNetwork.model,
      structureContextWindow = this)

    this.sequence.add(structure)

    this.curStateIndex = this.sequence.lastIndex
  }

  /**
   *
   */
  private fun forwardCurrentState(featuresArray: NDArray, useDropout: Boolean = false) {
    this.sequence.lastStructure!!.forward(features = featuresArray, useDropout = useDropout)
  }

  /**
   * reset
   */
  private fun reset() {
    this.sequence.reset()
    this.paramsErrorsAccumulator.reset()
  }

  /**
   *
   * @return
   */
  fun getInputSequenceErrors(copy: Boolean = true): Array<NDArray> = Array(size = this.sequence.length, init = {
    if (copy) {
      this.sequence.states[it].structure.inputLayer.inputArray.errors.copy()
    } else {
      this.sequence.states[it].structure.inputLayer.inputArray.errors
    }
  })

  /**
   *
   * @return
   */
  fun getOutputSequence(copy: Boolean = true): Array<NDArray> =
    Array(size = this.sequence.length, init = {
      if (copy) {
        this.sequence.states[it].structure.outputLayer.outputArray.values.copy()
      } else {
        this.sequence.states[it].structure.outputLayer.outputArray.values
      }
    })

  /**
   *
   * @param sequenceFeaturesArray features for each item of the sequence
   * @return the last output of the network after the whole sequence is been forwarded
   */
  fun forward(sequenceFeaturesArray: ArrayList<NDArray>): NDArray {

    sequenceFeaturesArray.forEachIndexed { i, features ->
      this.forward(features, firstState = (i == 0))
    }

    return this.getOutput()
  }

  /**
   *
   * @param featuresArray features
   */
  fun forward(featuresArray: NDArray, firstState: Boolean, useDropout: Boolean = false): NDArray {

    if (firstState) {
      this.reset()
    }

    this.addNewState()
    this.forwardCurrentState(featuresArray = featuresArray, useDropout = useDropout)

    return this.getOutput()
  }

  /**
   *
   * @param outputErrors output error
   */
  fun backward(outputErrors: NDArray, propagateToInput: Boolean = false) {

    val outputErrorsSequence = Array(
      size = this.sequence.length,
      init = {i -> if (sequence.isLast(i)) outputErrors else this.zeroErrors})

    this.backward(outputErrorsSequence = outputErrorsSequence, propagateToInput = propagateToInput)
  }

  /**
   *
   * @param outputErrorsSequence output errors for each item of the sequence
   */
  fun backward(outputErrorsSequence: Array<NDArray>, propagateToInput: Boolean = false) {

    require(outputErrorsSequence.size == this.sequence.length) {
      "Number of errors (${outputErrorsSequence.size}) does not " +
        "reflect the length of the sequence (${this.sequence.length})"
    }

    for ((i, state) in this.sequence.states.withIndex().reversed()) {

      this.curStateIndex = i // crucial to provide the right context

      state.structure.backward(
        outputErrors = outputErrorsSequence[i],
        paramsErrors = this.backwardParamsErrors,
        propagateToInput = propagateToInput)

      this.paramsErrorsAccumulator.accumulate(this.backwardParamsErrors)
    }

    this.paramsErrorsAccumulator.averageErrors()
  }

}