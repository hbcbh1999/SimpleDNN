/* Copyright 2016-present The KotlinNLP Authors. All Rights Reserved.
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, you can obtain one at http://mozilla.org/MPL/2.0/.
 * ------------------------------------------------------------------*/

package com.kotlinnlp.simplednn.deeplearning.attentionnetwork

import com.kotlinnlp.simplednn.core.arrays.UpdatableDenseArray
import com.kotlinnlp.simplednn.core.functionalities.updatemethods.UpdateMethod
import com.kotlinnlp.simplednn.core.optimizer.Optimizer
import com.kotlinnlp.simplednn.simplemath.ndarray.NDArray
import com.kotlinnlp.simplednn.simplemath.ndarray.dense.DenseNDArray
import com.kotlinnlp.simplednn.simplemath.ndarray.sparse.SparseNDArray
import com.kotlinnlp.simplednn.utils.scheduling.BatchScheduling
import com.kotlinnlp.simplednn.utils.scheduling.EpochScheduling
import com.kotlinnlp.simplednn.utils.scheduling.ExampleScheduling

/**
 * The optimizer of the parameters of the [AttentionNetwork]
 *
 * @param attentionNetwork the Attention Network to optimize
 */
class AttentionNetworkParamsOptimizer<InputNDArrayType: NDArray<InputNDArrayType>>(
  val attentionNetwork: AttentionNetwork<InputNDArrayType>,
  val updateMethod: UpdateMethod,
  val minLossCountToUpdate: Int = 1
) : Optimizer {

  /**
   * The accumulator of errors of the network parameters.
   */
  private val paramsErrorsAccumulator = AttentionNetworkParamsErrorsAccumulator(this.attentionNetwork)

  /**
   * Calculate the errors average and update the params.
   */
  override fun update() {

    if (this.paramsErrorsAccumulator.count >= this.minLossCountToUpdate) {

      this.paramsErrorsAccumulator.averageErrors()
      this.updateParams()
    }
  }

  /**
   * Method to call every new epoch.
   * In turn it calls the same method into the `updateMethod`
   */
  override fun newEpoch() {

    if (this.updateMethod is EpochScheduling) {
      this.updateMethod.newEpoch()
    }
  }

  /**
   * Method to call every new batch.
   * In turn it calls the same method into the `updateMethod`
   */
  override fun newBatch() {

    if (this.updateMethod is BatchScheduling) {
      this.updateMethod.newBatch()
    }
  }

  /**
   * Method to call every new example.
   * In turn it calls the same method into the `updateMethod`
   */
  override fun newExample() {

    if (this.updateMethod is ExampleScheduling) {
      this.updateMethod.newExample()
    }
  }

  /**
   * Accumulate the given [paramsErrors] into the accumulator.
   *
   * @param paramsErrors the network parameters errors to accumulate
   */
  fun accumulate(paramsErrors: AttentionNetworkParameters) {
    this.paramsErrorsAccumulator.accumulate(paramsErrors)
  }

  /**
   * Update the params in respect of the errors using the update method helper (Learning Rate, ADAM, AdaGrad, ...).
   */
  private fun updateParams() {

    val accumulatedErrors: AttentionNetworkParameters = this.paramsErrorsAccumulator.getParamsErrors()

    this.updateMethod.update(
      array = this.attentionNetwork.model.attentionParams.contextVector,
      errors = accumulatedErrors.attentionParams.contextVector.values)

    this.attentionNetwork.model.transformParams.zip(accumulatedErrors.transformParams).forEach { (params, errors) ->

      val e = errors.values

      when (e) {
        is DenseNDArray -> this.updateMethod.update(array = params as UpdatableDenseArray, errors = e)
        is SparseNDArray -> this.updateMethod.update(array = params as UpdatableDenseArray, errors = e)
        else -> throw RuntimeException("Invalid errors type")
      }
    }
  }
}
