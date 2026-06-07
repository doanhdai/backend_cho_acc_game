package com.shopaccgame.repository;

import com.shopaccgame.model.Account;
import java.util.List;

public interface AccountRepositoryCustom {
    class SearchResult {
        private List<Account> accounts;
        private long total;

        public SearchResult(List<Account> accounts, long total) {
            this.accounts = accounts;
            this.total = total;
        }

        public List<Account> getAccounts() { return accounts; }
        public long getTotal() { return total; }
    }

    SearchResult searchAccounts(String category, String search, Double priceMin, Double priceMax, String rank, List<Integer> skinIds, String sort, String order, int page, int limit);
}
