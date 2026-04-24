package pl.cramber.assetstore.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.cramber.assetstore.entity.Transaction;
import pl.cramber.assetstore.entity.User;
import pl.cramber.assetstore.repository.TransactionRepository;
import pl.cramber.assetstore.repository.UserRepository;

@Service
@RequiredArgsConstructor
public class WalletService {

    private final UserRepository userRepository;
    private final TransactionRepository transactionRepository;

    @Transactional
    public void addFunds(String robloxId, Integer amount) {
        User user = userRepository.findByRobloxId(robloxId)
                .orElseThrow(() -> new RuntimeException("USER_NOT_FOUND"));

        user.setBalance(user.getBalance() + amount);
        userRepository.save(user);

        Transaction transaction = Transaction.builder()
                .user(user)
                .amount(amount)
                .type("ROBLOX_DEPOSIT")
                .build();
        transactionRepository.save(transaction);
    }
}