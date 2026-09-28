package fe.DE201004;

import com.hfs302.pt1.Account;
import com.hfs302.pt1.AccountService;
import com.hfs302.pt1.AccountStatus;
import com.hfs302.pt1.ResultCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.LocalDate;
import java.util.Optional;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AccountServiceTest {

    private static final String USER = "alice_01";
    private static final String EMAIL = "alice@example.com";
    private static final String PASS = "Secret@123";
    private static final String PHONE = "0912345678";
    private static final LocalDate DOB = LocalDate.now().minusYears(20);

    AccountService service;

    @BeforeEach
    void setUp() {
        service = new AccountService();
    }

    @Nested
    class Register {
        
        @Test
        void register_ValidInput_ReturnsSuccessAndCorrectState() {
            // Act
            ResultCode result = service.register(USER, "ALICE@EXAMPLE.COM", PASS, PASS, DOB, PHONE);
            
            // Assert ResultCode
            assertEquals(ResultCode.SUCCESS, result);
            
            // Assert State
            Optional<Account> accOpt = service.findByUsername(USER);
            assertTrue(accOpt.isPresent());
            Account acc = accOpt.get();
            
            assertEquals(AccountStatus.ACTIVE, acc.getStatus());
            assertEquals(0, acc.getFailedAttempts());
            assertFalse(acc.isLocked());
            assertEquals("alice@example.com", acc.getEmail(), "Email must be saved in lowercase");
            assertNotEquals(PASS, acc.getCurrentPasswordHash(), "Hash must not equal plain password");
        }

        @ParameterizedTest(name = "[{index}] {0}")
        @MethodSource("fe.DE201004.AccountServiceTest#invalidRegisterInputs")
        void register_InvalidInput_ReturnsExpectedCode(String desc, String u, String e, String p, String c,
                                                       LocalDate dob, String phone, ResultCode expected) {
            assertEquals(expected, service.register(u, e, p, c, dob, phone));
            assertTrue(service.findByUsername(u).isEmpty(), "Account must not be created on failure");
        }

        @ParameterizedTest(name = "[{index}] today - {0} năm + {1} ngày -> {2}")
        @CsvSource({
            "18, 0, SUCCESS", 
            "18, 1, UNDERAGE", 
            "0, 1, INVALID_INPUT"
        })
        void register_AgeBoundary(int yearsAgo, int plusDays, ResultCode expected) {
            LocalDate dob = LocalDate.now().minusYears(yearsAgo).plusDays(plusDays);
            assertEquals(expected, service.register(USER, EMAIL, PASS, PASS, dob, null));
        }

        @ParameterizedTest
        @NullAndEmptySource
        @ValueSource(strings = {" "})
        void register_NullEmptyBlankUsername_ReturnsInvalidInput(String username) {
            assertEquals(ResultCode.INVALID_INPUT, service.register(username, EMAIL, PASS, PASS, DOB, PHONE));
        }
        
        @ParameterizedTest
        @NullAndEmptySource
        @ValueSource(strings = {" "})
        void register_NullEmptyBlankEmail_ReturnsInvalidInput(String email) {
            assertEquals(ResultCode.INVALID_INPUT, service.register(USER, email, PASS, PASS, DOB, PHONE));
        }
        
        @ParameterizedTest
        @NullAndEmptySource
        @ValueSource(strings = {" "})
        void register_NullEmptyBlankPassword_ReturnsInvalidInput(String password) {
            assertEquals(ResultCode.INVALID_INPUT, service.register(USER, EMAIL, password, password, DOB, PHONE));
        }

        @ParameterizedTest
        @ValueSource(strings = {"ALICE_01", "alice_01", "Alice_01"})
        void register_DuplicateUsername_CaseInsensitive(String duplicateUser) {
            service.register(USER, EMAIL, PASS, PASS, DOB, PHONE);
            assertEquals(ResultCode.DUPLICATE_USERNAME, service.register(duplicateUser, "bob@example.com", PASS, PASS, DOB, PHONE));
        }
        
        @ParameterizedTest
        @ValueSource(strings = {"ALICE@EXAMPLE.COM", "alice@example.com", "Alice@Example.com"})
        void register_DuplicateEmail_CaseInsensitive(String duplicateEmail) {
            service.register(USER, EMAIL, PASS, PASS, DOB, PHONE);
            assertEquals(ResultCode.DUPLICATE_EMAIL, service.register("bob_01", duplicateEmail, PASS, PASS, DOB, PHONE));
        }
    }

    static Stream<Arguments> invalidRegisterInputs() {
        return Stream.of(
            // Các lỗi đơn lẻ (vi phạm 1 rule)
            Arguments.of("username sai", "1alice", EMAIL, PASS, PASS, DOB, PHONE, ResultCode.INVALID_USERNAME),
            Arguments.of("email sai", USER, "bad", PASS, PASS, DOB, PHONE, ResultCode.INVALID_EMAIL),
            Arguments.of("mk yếu", USER, EMAIL, "weak", "weak", DOB, PHONE, ResultCode.WEAK_PASSWORD),
            Arguments.of("mk không khớp", USER, EMAIL, PASS, "Wrong@123", DOB, PHONE, ResultCode.PASSWORD_MISMATCH),
            Arguments.of("chưa đủ tuổi", USER, EMAIL, PASS, PASS, LocalDate.now().minusYears(17), PHONE, ResultCode.UNDERAGE),
            Arguments.of("phone sai", USER, EMAIL, PASS, PASS, DOB, "999", ResultCode.INVALID_PHONE),
            
            // Các lỗi ưu tiên (vi phạm nhiều rule cùng lúc -> trả về lỗi được kiểm tra trước)
            // REG-02 (INVALID_USERNAME) ưu tiên hơn REG-04 (INVALID_EMAIL)
            Arguments.of("username sai + email sai", "1alice", "bad", PASS, PASS, DOB, PHONE, ResultCode.INVALID_USERNAME),
            
            // REG-04 (INVALID_EMAIL) ưu tiên hơn REG-06 (WEAK_PASSWORD)
            Arguments.of("email sai + mk yếu", USER, "bad", "weak", "weak", DOB, PHONE, ResultCode.INVALID_EMAIL),
            
            // REG-06 (WEAK_PASSWORD) ưu tiên hơn REG-07 (PASSWORD_MISMATCH)
            Arguments.of("mk yếu + confirm lệch", USER, EMAIL, "weak", "x", DOB, PHONE, ResultCode.WEAK_PASSWORD),
            
            // REG-07 (PASSWORD_MISMATCH) ưu tiên hơn REG-08 (UNDERAGE)
            Arguments.of("mk không khớp + chưa đủ tuổi", USER, EMAIL, PASS, "Wrong@123", LocalDate.now().minusYears(17), PHONE, ResultCode.PASSWORD_MISMATCH)
        );
    }
}
