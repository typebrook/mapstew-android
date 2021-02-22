package io.typebrook.mapstew.livedata

import androidx.lifecycle.LiveData

@Suppress("UNCHECKED_CAST")
open class SafeMutableLiveData<T>(initValue: T) : LiveData<T>(initValue) {

    override fun getValue(): T = super.getValue() as T

    public override fun setValue(newValue: T) = if (predicate(newValue))
        super.setValue(transformer(newValue)) else
        Unit

    public override fun postValue(newValue: T) = if (predicate(newValue))
        super.postValue(transformer(newValue)) else
        Unit

    protected open val predicate: (T) -> Boolean = { newValue -> newValue != value }
    protected open val transformer: (T) -> T = { new -> new }
}

