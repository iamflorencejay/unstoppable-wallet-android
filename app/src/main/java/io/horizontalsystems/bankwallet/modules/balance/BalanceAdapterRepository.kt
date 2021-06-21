package io.horizontalsystems.bankwallet.modules.balance

import io.horizontalsystems.bankwallet.core.AdapterState
import io.horizontalsystems.bankwallet.core.BalanceData
import io.horizontalsystems.bankwallet.core.IAdapterManager
import io.horizontalsystems.bankwallet.core.subscribeIO
import io.horizontalsystems.bankwallet.entities.Wallet
import io.reactivex.Observable
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.disposables.Disposable
import io.reactivex.subjects.PublishSubject

class BalanceAdapterRepository(
    private val adapterManager: IAdapterManager,
    private val balanceCache: BalanceCache
) {
    private var wallets = listOf<Wallet>()

    private val updatesDisposables = CompositeDisposable()
    private var adapterReadyDisposable: Disposable? = null

    private val updatesSubject = PublishSubject.create<Wallet>()
    val updatesObservable: Observable<Wallet>
        get() = updatesSubject
            .doOnSubscribe {
                subscribeForAdapterReadyUpdate()
                subscribeForAdapterUpdates()
            }
            .doFinally {
                unsubscribeFromAdapterReadyUpdate()
                unsubscribeFromAdapterUpdates()
            }

    private fun unsubscribeFromAdapterReadyUpdate() {
        adapterReadyDisposable?.dispose()
    }

    private fun subscribeForAdapterReadyUpdate() {
        adapterReadyDisposable = adapterManager.adaptersReadyObservable
            .subscribeIO {
                unsubscribeFromAdapterUpdates()

                wallets.forEach { wallet ->
                    updatesSubject.onNext(wallet)
                }

                subscribeForAdapterUpdates()
            }
    }

    fun setWallet(wallets: List<Wallet>) {
        unsubscribeFromAdapterUpdates()
        this.wallets = wallets
        subscribeForAdapterUpdates()
    }

    private fun unsubscribeFromAdapterUpdates() {
        updatesDisposables.clear()
    }

    private fun subscribeForAdapterUpdates() {
        wallets.forEach { wallet ->
            adapterManager.getBalanceAdapterForWallet(wallet)?.let { adapter ->
                adapter.balanceStateUpdatedFlowable
                    .subscribeIO {
                        updatesSubject.onNext(wallet)
                    }
                    .let {
                        updatesDisposables.add(it)
                    }

                adapter.balanceUpdatedFlowable
                    .subscribeIO {
                        balanceCache.setCache(
                            wallet,
                            adapter.balanceData
                        )

                        updatesSubject.onNext(wallet)
                    }
                    .let {
                        updatesDisposables.add(it)
                    }
            }
        }
    }

    fun state(wallet: Wallet): AdapterState {
        return adapterManager.getBalanceAdapterForWallet(wallet)?.balanceState
            ?: AdapterState.Syncing(10, null)
    }

    fun balanceData(wallet: Wallet): BalanceData {
        return adapterManager.getBalanceAdapterForWallet(wallet)?.balanceData ?: balanceCache.getCache(wallet)
    }

    fun refresh() {
        adapterManager.refresh()
    }

    fun refreshByWallet(wallet: Wallet) {
        adapterManager.refreshByWallet(wallet)
    }
}