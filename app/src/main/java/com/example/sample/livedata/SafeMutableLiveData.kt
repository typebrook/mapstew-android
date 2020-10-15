package com.example.sample.livedata

import androidx.lifecycle.LiveData

@Suppress("UNCHECKED_CAST")
abstract class SafeMutableLiveData<T>(value: T) : LiveData<T>(value) {

    override fun getValue(): T = super.getValue() as T

    public override fun setValue(value: T) =
        if (predicate(value)) super.setValue(transformer(value)) else Unit

    public override fun postValue(value: T) =
        if (predicate(value)) super.postValue(transformer(value)) else Unit

    protected open val predicate: (value: T) -> Boolean = { true }
    protected open val transformer: (value: T) -> T = { new -> new }
}

