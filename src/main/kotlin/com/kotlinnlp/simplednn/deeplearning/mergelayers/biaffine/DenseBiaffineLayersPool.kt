/* Copyright 2016-present The KotlinNLP Authors. All Rights Reserved.
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, you can obtain one at http://mozilla.org/MPL/2.0/.
 * ------------------------------------------------------------------*/

package com.kotlinnlp.simplednn.deeplearning.mergelayers.biaffine

import com.kotlinnlp.simplednn.core.arrays.AugmentedArray
import com.kotlinnlp.simplednn.core.functionalities.activations.ActivationFunction
import com.kotlinnlp.simplednn.simplemath.ndarray.dense.DenseNDArray
import com.kotlinnlp.simplednn.utils.ItemsPool

/**
 * A pool of [BiaffineLayerStructure]s with dense input, which allows to allocate and release one when needed, without
 * creating a new one.
 * It is useful to optimize the creation of new structures every time a new encoder is created.
 *
 * @property params the parameters of the [BiaffineLayerStructure]s of the pool
 * @property activationFunction the activation function of the [BiaffineLayerStructure]s of the pool
 * @property dropout the probability of dropout (default 0.0) when generating the attention arrays for the Attention
 *                   Layer. If applying it, the usual value is 0.5 (better 0.25 if it's the first layer).
 */
class DenseBiaffineLayersPool(
  val params: BiaffineLayerParameters,
  val activationFunction: ActivationFunction,
  val dropout: Double = 0.0
) : ItemsPool<BiaffineLayerStructure<DenseNDArray>>() {

  /**
   * The factory of a new [BiaffineLayerStructure].
   *
   * @param id the unique id of the item to create
   *
   * @return a new [BiaffineLayerStructure] with the given [id]
   */
  override fun itemFactory(id: Int) = BiaffineLayerStructure<DenseNDArray>(
    inputArray1 = AugmentedArray(this.params.inputSize1),
    inputArray2 = AugmentedArray(this.params.inputSize2),
    outputArray = AugmentedArray(this.params.outputSize),
    params = this.params,
    activationFunction = this.activationFunction,
    dropout = this.dropout,
    id = id)
}
