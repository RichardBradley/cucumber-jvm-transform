package myapp.steps;


import com.google.common.base.Strings;
import com.google.common.collect.Iterables;
import cucumber.api.java.en.Given;
import cucumber.api.java.en.Then;
import cucumber.api.java.en.When;
import cucumber.api.java8.En;
import java.io.IOException;
import java.util.Arrays;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.mail.Address;
import javax.mail.MessagingException;
import javax.mail.internet.MimeMessage;
import myapp.MyAppWebConnector;
import myapp.config.MyAppParameters;
import myapp.connectors.EmailConnector;
import org.openqa.selenium.By;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;


/**
 * Integration tests steps for "change password" and "reset password" features.
 */
@SuppressWarnings("unused")
public class PasswordChangeSteps implements En {
    private static final Pattern PASSWORD_RESET_URL = Pattern.compile((("http[^ ]+" + (Pattern.quote(((MyAppParameters.getPathForPublicLandingPage()) + "?reset_password=")))) + "([a-z0-9]+)"));

    private MimeMessage lastEmail;

    public PasswordChangeSteps(MyAppWebConnector webConnector, EmailConnector emailConnector, UserSteps userSteps, LoginSteps loginSteps) {
    }

    private static String extractResetUrl(MimeMessage emailMessage) {
        try {
            String messageText = ((String) (emailMessage.getContent()));
            Matcher matcher = PasswordChangeSteps.PASSWORD_RESET_URL.matcher(messageText);
            matcher.find();
            return matcher.group(0);
        } catch (IOException | MessagingException e) {
            throw new RuntimeException(e);
        }
    }

    @Given("^I am a user with password \"([^\"]*)\"$")
    public void given_I_am_a_user_with_password_() {
        userSteps::createUserWithPassword();
    }

    @Given("^I am a user with email \"([^\"]*)\"$")
    public void given_I_am_a_user_with_email_() {
        userSteps::createUserWithEmail();
    }

    @Given("^I am (not )?logged in to the MyApp website$")
    public void given_I_am_not_logged_in_to_the_MyApp_website(String not) {
        boolean expectationIsPositive = Strings.isNullOrEmpty(not);
        if (expectationIsPositive) {
            userSteps.logInCurrentUser();
        }else {
            userSteps.hardLogout();
        }
    }

    @Then("^I should see a message confirming my password has changed$")
    public void then_I_should_see_a_message_confirming_my_password_has_changed() {
        assertThat(webConnector.getDriver().findElement(By.cssSelector(".positive-feedback.change-password-message")).getText()).isEqualTo("Password successfully updated");
    }

    @When("^I log in with my username and password \"([^\"]*)\"")
    public void when_I_log_in_with_my_username_and_password_(String password) {
        userSteps.logIn(userSteps.getCurrentUsername(), password);
    }

    @Then("^I should see a message telling me my password was wrong$")
    public void then_I_should_see_a_message_telling_me_my_password_was_wrong() {
        assertThat(webConnector.getDriver().findElement(By.cssSelector("#currentPassword ~ div.negative-feedback")).getText()).isEqualTo("Incorrect password entered");
    }

    @When("^I click \"Forgot Password\" link$")
    public void when_I_click_Forgot_Password_link() {
        webConnector.clickElement(webConnector.getDriver().findElement(By.cssSelector(".forgot-password-link span")));
    }

    @When("^I open the Forgot Password form$")
    public void when_I_open_the_Forgot_Password_form() {
        loginSteps.setLoginFormVisibility(true);
        WebDriverWait wait = webConnector.defaultWait();
        wait.until(ExpectedConditions.visibilityOfElementLocated(byTestId("forgot-password-button"))).click();
    }

    @When("^I request a password reset$")
    public void when_I_request_a_password_reset() {
        // Be sure to clear the text box, as it may have been autofilled with the user's email address by the previous step
        WebElement usernameInputField = webConnector.defaultWait().until(ExpectedConditions.presenceOfElementLocated(By.cssSelector("input#username-forgot")));
        usernameInputField.clear();
        usernameInputField.sendKeys(userSteps.getCurrentUsername());
        webConnector.clickElementByTestId("request-password-reset");
    }

    @Then("^I receive a password reset email$")
    public void then_I_receive_a_password_reset_email() {
        Optional<MimeMessage> email = emailConnector.waitForAnEmail();
        lastEmail = email.get();
        Address[] recipients = new Address[0];
        String subject;
        try {
            recipients = lastEmail.getAllRecipients();
            subject = lastEmail.getSubject();
        } catch ( e) {
            throw new <e>RuntimeException();
        }
        String recipientEmail = Iterables.getOnlyElement(Arrays.asList(recipients)).toString();
        assertThat(recipientEmail).isEqualTo(userSteps.getCurrentUsername());
        assertThat(subject.toLowerCase()).contains("password reset");
    }

    @When("^I navigate away from the site$")
    public void when_I_navigate_away_from_the_site() {
        webConnector.getDriver().navigate().to("http://example.com/404");// Ensure that angular isn't loaded

    }

    @Then("^I open the password reset link$")
    public void then_I_open_the_password_reset_link() {
        String fullUrl = extractResetUrl(lastEmail);
        webConnector.getDriver().navigate().to("http://example.com/404");// Ensure that angular isn't loaded

        webConnector.openAngularSitePageByFullUrl(fullUrl);
    }

    @When("^I set a new password \"([^\"]*)\"$")
    public void when_I_set_a_new_password_(String password) {
        WebDriverWait wait = webConnector.defaultWait();
        wait.until(ExpectedConditions.presenceOfElementLocated(By.cssSelector("input#newPassword"))).sendKeys(password);
        wait.until(ExpectedConditions.presenceOfElementLocated(By.cssSelector("input#confirmNewPassword"))).sendKeys(password);
        webConnector.clickElementByTestId("set-password-button");
        userSteps.setCurrentPassword(password);
    }

    @Then("^I should see a message telling me to use a stronger password$")
    public void then_I_should_see_a_message_telling_me_to_use_a_stronger_password() {
        assertThatWebElement(webConnector.waitAndScrollToElement(By.xpath(String.format("//*[contains(text(), \"%s\")]", "Password must be")))).isDisplayed();
    }
}

