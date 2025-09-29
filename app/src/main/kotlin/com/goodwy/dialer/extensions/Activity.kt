package com.goodwy.dialer.extensions

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.provider.ContactsContract
import android.telecom.PhoneAccount
import android.telecom.PhoneAccountHandle
import android.telecom.TelecomManager
import android.view.View
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.content.res.ResourcesCompat
import com.goodwy.commons.activities.BaseSimpleActivity
import com.goodwy.commons.dialogs.CallConfirmationDialog
import com.goodwy.commons.dialogs.NewAppDialog
import com.goodwy.commons.extensions.*
import com.goodwy.commons.helpers.*
import com.goodwy.commons.models.FAQItem
import com.goodwy.commons.models.contacts.Contact
import com.goodwy.dialer.BuildConfig
import com.goodwy.dialer.activities.DialerActivity
import com.goodwy.dialer.R
import com.goodwy.dialer.activities.SimpleActivity
import com.goodwy.dialer.dialogs.SelectSIMDialog
import com.goodwy.dialer.dialogs.SelectSimButtonDialog
import com.goodwy.dialer.helpers.SIM_DIALOG_STYLE_LIST
import com.google.android.material.snackbar.Snackbar

fun SimpleActivity.startCallIntent(recipient: String) {
    if (isDefaultDialer()) {
        getHandleToUse(null, recipient) { handle ->
            launchCallIntent(recipient, handle, BuildConfig.RIGHT_APP_KEY)
        }
    } else {
        launchCallIntent(recipient, null, BuildConfig.RIGHT_APP_KEY)
    }
}

fun SimpleActivity.startCallWithConfirmationCheck(recipient: String, name: String) {
    if (config.showCallConfirmation) {
        CallConfirmationDialog(this, name) {
            startCallIntent(recipient)
        }
    } else {
        startCallIntent(recipient)
    }
}

fun SimpleActivity.launchCreateNewContactIntent() {
    Intent().apply {
        action = Intent.ACTION_INSERT
        data = ContactsContract.Contacts.CONTENT_URI
        launchActivityIntent(this)
    }
}

fun BaseSimpleActivity.callContactWithSim(recipient: String, useMainSIM: Boolean) {
    handlePermission(PERMISSION_READ_PHONE_STATE) {
        val wantedSimIndex = if (useMainSIM) 0 else 1
        val handle = getAvailableSIMCardLabels().sortedBy { it.id }.getOrNull(wantedSimIndex)?.handle
        launchCallIntent(recipient, handle, BuildConfig.RIGHT_APP_KEY)
    }
}

fun BaseSimpleActivity.callContactWithSimWithConfirmationCheck(recipient: String, name: String, useMainSIM: Boolean) {
    if (config.showCallConfirmation) {
        CallConfirmationDialog(this, name) {
            callContactWithSim(recipient, useMainSIM)
        }
    } else {
        callContactWithSim(recipient, useMainSIM)
    }
}

fun Activity.launchSendSMSIntentRecommendation(recipient: String) {
    val simpleSmsMessenger = "com.goodwy.smsmessenger"
    val simpleSmsMessengerDebug = "com.goodwy.smsmessenger.debug"
    if ((0..config.appRecommendationDialogCount).random() == 2 && (!isPackageInstalled(simpleSmsMessenger) && !isPackageInstalled(simpleSmsMessengerDebug))) {
        NewAppDialog(this, simpleSmsMessenger, getString(R.string.recommendation_dialog_messages_g), getString(R.string.right_sms_messenger),
            AppCompatResources.getDrawable(this, R.drawable.ic_sms_messenger)) {
            launchSendSMSIntent(recipient)
        }
    } else {
        launchSendSMSIntent(recipient)
    }
}

fun Activity.startContactDetailsIntentRecommendation(contact: Contact) {
    val simpleContacts = "com.goodwy.contacts"
    val simpleContactsDebug = "com.goodwy.contacts.debug"
    if ((0..config.appRecommendationDialogCount).random() == 2 && (!isPackageInstalled(simpleContacts) && !isPackageInstalled(simpleContactsDebug))) {
        NewAppDialog(this, simpleContacts, getString(R.string.recommendation_dialog_contacts_g), getString(R.string.right_contacts),
            AppCompatResources.getDrawable(this, R.drawable.ic_contacts)) {
            startContactDetailsIntent(contact)
        }
    } else {
        startContactDetailsIntent(contact)
    }
}

// handle private contacts differently, only Goodwy Contacts can open them
fun Activity.startContactDetailsIntent(contact: Contact) {
    val simpleContacts = "com.goodwy.contacts"
    val simpleContactsDebug = "com.goodwy.contacts.debug"
    if (contact.rawId > 1000000 && contact.contactId > 1000000 && contact.rawId == contact.contactId &&
        (isPackageInstalled(simpleContacts) || isPackageInstalled(simpleContactsDebug))
    ) {
        Intent().apply {
            action = Intent.ACTION_VIEW
            putExtra(CONTACT_ID, contact.rawId)
            putExtra(IS_PRIVATE, true)
            `package` = if (isPackageInstalled(simpleContacts)) simpleContacts else simpleContactsDebug
            setDataAndType(ContactsContract.Contacts.CONTENT_LOOKUP_URI, "vnd.android.cursor.dir/person")
            launchActivityIntent(this)
        }
    } else {
        ensureBackgroundThread {
            val lookupKey = SimpleContactsHelper(this).getContactLookupKey((contact).rawId.toString())
            val publicUri = Uri.withAppendedPath(ContactsContract.Contacts.CONTENT_LOOKUP_URI, lookupKey)
            runOnUiThread {
                launchViewContactIntent(publicUri)
            }
        }
    }
}

// used at devices with multiple SIM cards
@SuppressLint("MissingPermission")
fun SimpleActivity.getHandleToUse(intent: Intent?, phoneNumber: String, callback: (handle: PhoneAccountHandle?) -> Unit) {
    handlePermission(PERMISSION_READ_PHONE_STATE) {
        if (it) {
            val defaultHandle = telecomManager.getDefaultOutgoingPhoneAccount(PhoneAccount.SCHEME_TEL)
            when {
                intent?.hasExtra(TelecomManager.EXTRA_PHONE_ACCOUNT_HANDLE) == true -> callback(intent.getParcelableExtra(TelecomManager.EXTRA_PHONE_ACCOUNT_HANDLE)!!)
                config.getCustomSIM(phoneNumber) != null && areMultipleSIMsAvailable() -> callback(config.getCustomSIM(phoneNumber))
                defaultHandle != null -> callback(defaultHandle)
                else -> {
                    if (config.simDialogStyle == SIM_DIALOG_STYLE_LIST) {
                        SelectSIMDialog(this, phoneNumber, onDismiss = {
                            if (this is DialerActivity) {
                                finish()
                            }
                        }) { handle, _ ->
                            callback(handle)
                        }
                    } else {
                        SelectSimButtonDialog(this, phoneNumber, onDismiss = {
                            if (this is DialerActivity) {
                                finish()
                            }
                        }) { handle, _ ->
                            callback(handle)
                        }
                    }
                }
            }
        }
    }
}

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
        R.string.app_name_g,
        productIdList = arrayListOf(productIdX1, productIdX2, productIdX3),
        productIdListRu = arrayListOf(productIdX1, productIdX2, productIdX3),
        subscriptionIdList = arrayListOf(subscriptionIdX1, subscriptionIdX2, subscriptionIdX3),
        subscriptionIdListRu = arrayListOf(subscriptionIdX1, subscriptionIdX2, subscriptionIdX3),
        subscriptionYearIdList = arrayListOf(subscriptionYearIdX1, subscriptionYearIdX2, subscriptionYearIdX3),
        subscriptionYearIdListRu = arrayListOf(subscriptionYearIdX1, subscriptionYearIdX2, subscriptionYearIdX3),
        playStoreInstalled = isPlayStoreInstalled(),
        ruStoreInstalled = isRuStoreInstalled()
    )
}

fun SimpleActivity.launchAbout() {
    val licenses = LICENSE_GLIDE or LICENSE_INDICATOR_FAST_SCROLL

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

    startAboutActivity(
        appNameId = R.string.app_name_g,
        licenseMask = licenses,
        versionName = BuildConfig.VERSION_NAME,
        faqItems = faqItems,
        showFAQBeforeMail = true,
        productIdList = arrayListOf(productIdX1, productIdX2, productIdX3),
        productIdListRu = arrayListOf(productIdX1, productIdX2, productIdX3),
        subscriptionIdList = arrayListOf(subscriptionIdX1, subscriptionIdX2, subscriptionIdX3),
        subscriptionIdListRu = arrayListOf(subscriptionIdX1, subscriptionIdX2, subscriptionIdX3),
        subscriptionYearIdList = arrayListOf(subscriptionYearIdX1, subscriptionYearIdX2, subscriptionYearIdX3),
        subscriptionYearIdListRu = arrayListOf(subscriptionYearIdX1, subscriptionYearIdX2, subscriptionYearIdX3),
        playStoreInstalled = isPlayStoreInstalled(),
        ruStoreInstalled = isRuStoreInstalled()
    )
}

fun SimpleActivity.showSnackbar(view: View) {
    view.performHapticFeedback()

    val snackbar = Snackbar.make(view, R.string.support_project_to_unlock, Snackbar.LENGTH_SHORT)
        .setAction(R.string.support) {
            launchPurchase()
        }

    val bgDrawable = ResourcesCompat.getDrawable(view.resources, R.drawable.button_background_16dp, null)
    snackbar.view.background = bgDrawable
    val properBackgroundColor = getProperBackgroundColor()
    val backgroundColor = if (properBackgroundColor == Color.BLACK) getBottomNavigationBackgroundColor().lightenColor(6) else getBottomNavigationBackgroundColor().darkenColor(6)
    snackbar.setBackgroundTint(backgroundColor)
    snackbar.setTextColor(getProperTextColor())
    snackbar.setActionTextColor(getProperPrimaryColor())
    snackbar.show()
}
