package io.typebrook.mapstew.livedata

import androidx.lifecycle.LiveData

@Suppress("UNCHECKED_CAST")
open class SafeMutableLiveData<T>(initValue: T) : LiveData<T>(initValue) {

    override fun getValue(): T = super.getValue() as T

    public override fun setValue(value: T) = if (predicate(value))
        super.setValue(transformer(value)) else
        Unit

    public override fun postValue(value: T) = if (predicate(value))
        super.postValue(transformer(value)) else
        Unit

    protected open val predicate: (T) -> Boolean = { newValue -> newValue != value }
    protected open val transformer: (T) -> T = { new -> new }
}

