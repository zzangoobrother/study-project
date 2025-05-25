package com.example;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@RequiredArgsConstructor
@Service
public class TestService {

    private final BillingDetailsRepository billingDetailsRepository;
    private final BankAccountRepository bankAccountRepository;
    private final CreditCardRepository creditCardRepository;

    public void get1() {
        billingDetailsRepository.findAll();
    }

    public void save2() {
        BankAccount bankAccount = BankAccount.builder()
                .owner("owner bank")
                .bankName("국민")
                .account("1234")
                .swift("swift")
                .build();

        bankAccountRepository.save(bankAccount);
    }

    public void get2() {
        bankAccountRepository.findAll();
    }

    public void save3() {
        CreditCard creditCard = CreditCard.builder()
                .owner("owner credit")
                .cardNumber("1234")
                .expMonth("12")
                .expYear("2025")
                .build();

        creditCardRepository.save(creditCard);
    }

    public void get3() {
        creditCardRepository.findAll();
    }
}
