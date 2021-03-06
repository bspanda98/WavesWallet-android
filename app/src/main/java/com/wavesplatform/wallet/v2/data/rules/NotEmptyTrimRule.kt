/*
 * Created by Eduard Zaydel on 1/4/2019
 * Copyright © 2019 Waves Platform. All rights reserved.
 */

package com.wavesplatform.wallet.v2.data.rules

import androidx.annotation.StringRes

import io.github.anderscheow.validator.rules.BaseRule

class NotEmptyTrimRule : BaseRule {

    constructor() : super("Value must not be empty") {}

    constructor(@StringRes errorRes: Int) : super(errorRes) {}

    constructor(errorMessage: String) : super(errorMessage) {}

    override fun validate(value: Any?): Boolean {
        if (value == null) {
            throw NullPointerException()
        }

        if (value is String) {
            return !value.trim { it <= ' ' }.isEmpty()
        }

        throw ClassCastException("Required String value")
    }
}
