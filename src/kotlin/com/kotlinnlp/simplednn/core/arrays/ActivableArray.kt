/* Copyright 2016-present The KotlinNLP Authors. All Rights Reserved.
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, you can obtain one at http://mozilla.org/MPL/2.0/.
 * ------------------------------------------------------------------*/

package com.kotlinnlp.simplednn.core.arrays

import com.kotlinnlp.simplednn.core.functionalities.activations.ActivationFunction
import com.kotlinnlp.simplednn.simplemath.NDArray
import com.kotlinnlp.simplednn.simplemath.ndarray.Shape

/**
 * The ActivableArray is a superstructure of an NDArray on which values
 * are modified according to the activation function being used (e.g. Tanh, Sigmoid, ReLU, ELU).
 *
 * @property size the length of the array
 */
open class ActivableArray(val size: Int) {

  companion object {

    /**
     *
     * @param values the initial values to assign to the ActivableArray
     * @return an ActivableArray with the values already initialized
     */
    operator fun invoke(values: NDArray): ActivableArray {

      val array = ActivableArray(size = values.length)

      array.assignValues(values)

      return array
    }
  }

  /**
   * An NDArray containing the values of this ActivableArray
   */
  val values: NDArray get() = this._values

  /**
   * An NDArray containing the values not activated of this ActivableArray (respect on the last call of activate())
   */
  val valuesNotActivated: NDArray get() =
    if (this._valuesNotActivated != null)
      this._valuesNotActivated!!
    else
      this._values

  /**
   * The function used to activate this ActivableArray (e.g. Tanh, Sigmoid, ReLU, ELU)
   */
  var activationFunction: ActivationFunction? = null

  /**
   * Whether this array has an activation function
   */
  val hasActivation: Boolean get() = this.activationFunction != null

  /**
   * An NDArray containing the values of this ActivableArray
   */
  protected var _values: NDArray = NDArray.zeros(Shape(size))

  /**
   * An NDArray containing the values not activated of this ActivableArray (respect on the last call of activate())
   */
  protected var _valuesNotActivated: NDArray? = null

  /**
   * Assign values to the array
   * @param values values to assign to this ActivableArray
   */
  fun assignValues(values: NDArray) {
    this._values.assignValues(values)
  }

  /**
   *
   * @return set the activation function of this ActivableArray
   */
  open fun setActivation(activationFunction: ActivationFunction) {
    this.activationFunction = activationFunction
  }

  /**
   * Activate the array memorizing the values not activated and setting the new activated values
   */
  fun activate() {

    if (this.hasActivation) {

      if (this._valuesNotActivated == null) {
        this._valuesNotActivated = NDArray.emptyArray(this._values.shape)
      }

      this._valuesNotActivated!!.assignValues(this._values)

      this._values = this.activationFunction!!.f(this.valuesNotActivated)
    }
  }

  /**
   * Activate the array without modifying it, but only returning the values
   * @return the activated values
   */
  fun getActivatedValues(): NDArray {
    require(this.hasActivation)
    return this.activationFunction!!.f(this.valuesNotActivated)
  }

  /**
   *
   * @return the derivative of the activation calculated in valuesNotActivated (it uses an
   *         optimized function because all the common functions used as activation contain
   *         the activated values themselves in their derivative)
   */
  fun calculateActivationDeriv(): NDArray {
    return this.activationFunction!!.dfOptimized(this._values)
  }

  /**
   *
   * @return a clone of this ActivableArray
   */
  open fun clone(): ActivableArray {

    val clonedArray = ActivableArray(this.size)

    clonedArray._values.assignValues(this._values)

    if (this.hasActivation) {
      clonedArray._valuesNotActivated = this.valuesNotActivated.copy()
      clonedArray.setActivation(this.activationFunction!!)
    }

    return clonedArray
  }
}