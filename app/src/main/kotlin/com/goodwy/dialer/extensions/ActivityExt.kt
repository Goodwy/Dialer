package com.goodwy.dialer.extensions

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.provider.ContactsContract
import android.view.View
import androidx.appcompat.content.res.AppCompatResources
import com.goodwy.commons.dialogs.NewAppDialog
import com.goodwy.commons.extensions.getContactPublicUri
import com.goodwy.commons.extensions.isNewApp
import com.goodwy.commons.extensions.isPackageInstalled
import com.goodwy.commons.extensions.launchActivityIntent
import com.goodwy.commons.extensions.launchSendSMSIntent
import com.goodwy.commons.extensions.launchViewContactIntent
import com.goodwy.commons.extensions.showSupportSnackbar
import com.goodwy.commons.helpers.CONTACT_ID
import com.goodwy.commons.helpers.FIRST_CONTACT_ID
import com.goodwy.commons.helpers.IS_PRIVATE
import com.goodwy.commons.helpers.LICENSE_AUTOFITTEXTVIEW
import com.goodwy.commons.helpers.LICENSE_EVENT_BUS
import com.goodwy.commons.helpers.LICENSE_GLIDE
import com.goodwy.commons.helpers.LICENSE_INDICATOR_FAST_SCROLL
import com.goodwy.commons.helpers.SimpleContactsHelper
import com.goodwy.commons.helpers.ensureBackgroundThread
import com.goodwy.commons.models.FAQItem
import com.goodwy.commons.models.contacts.Contact
import com.goodwy.dialer.BuildConfig
import com.goodwy.dialer.R
import com.goodwy.dialer.activities.SimpleActivity

fun SimpleActivity.launchCreateNewContactIntent() {
    Intent().apply {
        action = Intent.ACTION_INSERT
        data = ContactsContract.Contacts.CONTENT_URI
        launchActivityIntent(this)
    }
}

// handle private contacts differently, only Simple Contacts Pro can open them
fun Activity.startContactDetailsIntent(contact: Contact) {
    val simpleContacts = "com.goodwy.contacts"
    val simpleContactsDebug = "com.goodwy.contacts.debug"
    val newContacts = "dev.goodwy.contacts"
    val newContactsDebug = "dev.goodwy.contacts.debug"
    val isPrivateContact = contact.rawId > FIRST_CONTACT_ID
        && contact.contactId > FIRST_CONTACT_ID
        && contact.rawId == contact.contactId
        && (isPackageInstalled(simpleContacts) || isPackageInstalled(simpleContactsDebug) ||
            isPackageInstalled(newContacts) || isPackageInstalled(newContactsDebug))
    if (isPrivateContact) {
        Intent().apply {
            action = Intent.ACTION_VIEW
            putExtra(CONTACT_ID, contact.rawId)
            putExtra(IS_PRIVATE, true)
            `package` = when {
                isPackageInstalled(newContacts) -> newContacts
                isPackageInstalled(simpleContacts) -> simpleContacts
                isPackageInstalled(newContactsDebug) -> newContactsDebug
                else -> simpleContactsDebug
            }
            setDataAndType(
                ContactsContract.Contacts.CONTENT_LOOKUP_URI,
                "vnd.android.cursor.dir/person"
            )
            launchActivityIntent(this)
        }
    } else {
        ensureBackgroundThread {
            val lookupKey =
                SimpleContactsHelper(this).getContactLookupKey((contact).rawId.toString())
            val publicUri =
                Uri.withAppendedPath(ContactsContract.Contacts.CONTENT_LOOKUP_URI, lookupKey)
            runOnUiThread {
                launchViewContactIntent(publicUri)
            }
        }
    }
}

//Goodwy
fun Activity.startContactEdit(contact: Contact) {
    Intent().apply {
        action = Intent.ACTION_EDIT
        data = getContactPublicUri(contact)
        launchActivityIntent(this)
    }
}

fun SimpleActivity.launchPurchase() {
    val productIdX1 = BuildConfig.PRODUCT_ID_X1
    val productIdX2 = BuildConfig.PRODUCT_ID_X2
    val productIdX3 = BuildConfig.PRODUCT_ID_X3
    val subscriptionIdX1 = BuildConfig.SUBSCRIPTION_ID_X1
    val subscriptionIdX2 = BuildConfig.SUBSCRIPTION_ID_X2
    val subscriptionIdX3 = BuildConfig.SUBSCRIPTION_ID_X3
    val subscriptionYearIdX1 = BuildConfig.SUBSCRIPTION_YEAR_ID_X1
    val subscriptionYearIdX2 = BuildConfig.SUBSCRIPTION_YEAR_ID_X2
    val subscriptionYearIdX3 = BuildConfig.SUBSCRIPTION_YEAR_ID_X3

    startPurchaseActivity(
        R.string.app_name,
        productIdList = arrayListOf(productIdX1, productIdX2, productIdX3),
        productIdListRu = arrayListOf(productIdX1, productIdX2, productIdX3),
        subscriptionIdList = arrayListOf(subscriptionIdX1, subscriptionIdX2, subscriptionIdX3),
        subscriptionIdListRu = arrayListOf(subscriptionIdX1, subscriptionIdX2, subscriptionIdX3),
        subscriptionYearIdList = arrayListOf(subscriptionYearIdX1, subscriptionYearIdX2, subscriptionYearIdX3),
        subscriptionYearIdListRu = arrayListOf(subscriptionYearIdX1, subscriptionYearIdX2, subscriptionYearIdX3),
    )
}

fun SimpleActivity.launchAbout() {
    val licenses = LICENSE_GLIDE or LICENSE_INDICATOR_FAST_SCROLL or LICENSE_AUTOFITTEXTVIEW or LICENSE_EVENT_BUS

    val faqItems = arrayListOf(
        FAQItem(R.string.faq_1_title, R.string.faq_1_text),
        FAQItem(R.string.faq_2_title, R.string.faq_2_text),
        FAQItem(R.string.faq_3_title, R.string.faq_3_text_g),
        FAQItem(R.string.faq_1_title_dialer_g, R.string.faq_1_text_dialer_g),
        FAQItem(R.string.faq_2_title_dialer_g, R.string.faq_2_text_dialer_g),
        FAQItem(R.string.faq_2_title_commons, R.string.faq_2_text_commons_g),
        FAQItem(R.string.faq_6_title_commons, R.string.faq_6_text_commons_g),
        FAQItem(R.string.faq_7_title_commons, R.string.faq_7_text_commons),
        FAQItem(R.string.faq_9_title_commons, R.string.faq_9_text_commons)
    )

    val productIdX1 = BuildConfig.PRODUCT_ID_X1
    val productIdX2 = BuildConfig.PRODUCT_ID_X2
    val productIdX3 = BuildConfig.PRODUCT_ID_X3
    val subscriptionIdX1 = BuildConfig.SUBSCRIPTION_ID_X1
    val subscriptionIdX2 = BuildConfig.SUBSCRIPTION_ID_X2
    val subscriptionIdX3 = BuildConfig.SUBSCRIPTION_ID_X3
    val subscriptionYearIdX1 = BuildConfig.SUBSCRIPTION_YEAR_ID_X1
    val subscriptionYearIdX2 = BuildConfig.SUBSCRIPTION_YEAR_ID_X2
    val subscriptionYearIdX3 = BuildConfig.SUBSCRIPTION_YEAR_ID_X3

    val flavorName = BuildConfig.FLAVOR
    val storeDisplayName = when (flavorName) {
        "gplay" -> "Google Play"
        "foss" -> "FOSS"
        "rustore" -> "RuStore"
        else -> ""
    }
    val versionName = BuildConfig.VERSION_NAME
    val fullVersionText = "$versionName ($storeDisplayName)"

    startAboutActivity(
        appNameId = R.string.app_name,
        licenseMask = licenses,
        versionName = fullVersionText,
        flavorName = BuildConfig.FLAVOR,
        faqItems = faqItems,
        showFAQBeforeMail = true,
        productIdList = arrayListOf(productIdX1, productIdX2, productIdX3),
        productIdListRu = arrayListOf(productIdX1, productIdX2, productIdX3),
        subscriptionIdList = arrayListOf(subscriptionIdX1, subscriptionIdX2, subscriptionIdX3),
        subscriptionIdListRu = arrayListOf(subscriptionIdX1, subscriptionIdX2, subscriptionIdX3),
        subscriptionYearIdList = arrayListOf(subscriptionYearIdX1, subscriptionYearIdX2, subscriptionYearIdX3),
        subscriptionYearIdListRu = arrayListOf(subscriptionYearIdX1, subscriptionYearIdX2, subscriptionYearIdX3),
    )
}

fun SimpleActivity.showSnackbar(view: View) {
    showSupportSnackbar(view) { launchPurchase() }
}

fun Activity.launchSendSMSIntentRecommendation(recipient: String) {
    val isNewApp = true //isNewApp()
    val simpleSmsMessenger = "com.goodwy.smsmessenger"
    val simpleSmsMessengerDebug = "com.goodwy.smsmessenger.debug"
    val newSimpleSmsMessenger = "dev.goodwy.messages"
    val newSimpleSmsMessengerDebug = "dev.goodwy.messages.debug"
    if ((0..config.appRecommendationDialogCount).random() == 2 &&
        (!isPackageInstalled(simpleSmsMessenger) && !isPackageInstalled(simpleSmsMessengerDebug) &&
            !isPackageInstalled(newSimpleSmsMessenger) && !isPackageInstalled(newSimpleSmsMessengerDebug))
    ) {
        NewAppDialog(
            activity = this,
            packageName = if (isNewApp) newSimpleSmsMessenger else simpleSmsMessenger,
            title = getString(R.string.recommendation_dialog_messages_g),
            text = if (isNewApp) "AlRight Messages" else getString(R.string.right_sms_messenger),
            drawable = AppCompatResources.getDrawable(
                this,
                if (isNewApp) R.drawable.ic_sms_messenger_new else R.drawable.ic_sms_messenger
            )
        ) {
            launchSendSMSIntent(recipient)
        }
    } else {
        launchSendSMSIntent(recipient)
    }
}

fun Activity.startContactDetailsIntentRecommendation(contact: Contact) {
    val isNewApp = true //isNewApp()
    val simpleContacts = "com.goodwy.contacts"
    val simpleContactsDebug = "com.goodwy.contacts.debug"
    val newContacts = "dev.goodwy.contacts"
    val newContactsDebug = "dev.goodwy.contacts.debug"
    if ((0..config.appRecommendationDialogCount).random() == 2 &&
        (!isPackageInstalled(simpleContacts) && !isPackageInstalled(simpleContactsDebug)  &&
            !isPackageInstalled(newContacts) && !isPackageInstalled(newContactsDebug))
    ) {
        NewAppDialog(
            activity = this,
            packageName = if (isNewApp) newContacts else simpleContacts,
            title = getString(R.string.recommendation_dialog_contacts_g),
            text = if (isNewApp) "AlRight Contacts" else getString(R.string.right_contacts),
            drawable = AppCompatResources.getDrawable(
                this,
                if (isNewApp) R.drawable.ic_contacts_new else R.drawable.ic_contacts)
        ) {
            startContactDetailsIntent(contact)
        }
    } else {
        startContactDetailsIntent(contact)
    }
}

fun Activity.newAppRecommendation() {
    if (resources.getBoolean(R.bool.is_foss)) {
        if (!isNewApp()) {
            if ((0..config.newAppRecommendationDialogCount).random() == 2) {
                val packageName = "enohp.ywdoog.ved".reversed()
                NewAppDialog(
                    activity = this,
                    packageName = packageName,
                    title = getString(com.goodwy.strings.R.string.notification_of_new_application),
                    text = "AlRight Phone",
                    drawable = AppCompatResources.getDrawable(this, R.drawable.ic_dialer_new),
                    showSubtitle = true
                ) {
                }
            }
        }
    }
}
