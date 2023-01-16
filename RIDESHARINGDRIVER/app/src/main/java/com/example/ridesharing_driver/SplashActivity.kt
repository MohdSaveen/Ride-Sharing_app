package com.example.ridesharing_driver

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.text.TextUtils
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.ridesharing_driver.Model.DriverInfoModel
import com.example.ridesharing_driver.Utils.UserUtils
import com.firebase.ui.auth.AuthMethodPickerLayout
import com.firebase.ui.auth.AuthUI
import com.firebase.ui.auth.IdpResponse
import com.google.android.material.textfield.TextInputEditText
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*
import com.google.firebase.messaging.FirebaseMessaging
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.core.Completable
import java.util.*
import java.util.concurrent.TimeUnit
import kotlinx.android.synthetic.main.activity_splash.*
import kotlinx.android.synthetic.main.layout_register.*
import kotlinx.android.synthetic.main.layout_register.view.*

class SplashActivity : AppCompatActivity() {

  companion object {
    private val LOGIN_REQUEST_CODE = 7171
  }

  private lateinit var provider: List<AuthUI.IdpConfig>
  private lateinit var firebaseauth: FirebaseAuth
  private lateinit var listener: FirebaseAuth.AuthStateListener

  private lateinit var database: FirebaseDatabase
  private lateinit var driverInfoReference: DatabaseReference

  override fun onStart() {
    super.onStart()
    delaySplashScreen()
  }

  private fun delaySplashScreen() {
    Completable.timer(3, TimeUnit.SECONDS, AndroidSchedulers.mainThread()).subscribe {
      firebaseauth.addAuthStateListener(listener)
    }
  }

  override fun onStop() {
    if (firebaseauth != null && listener != null) firebaseauth.removeAuthStateListener(listener)
    super.onStop()
  }

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    setContentView(R.layout.activity_splash)

    init()

  }

  private fun init() {

    database = FirebaseDatabase.getInstance()
    driverInfoReference = database.getReference(Common.DRIVER_INFO_REFERENCE)
    provider =
        Arrays.asList(
            AuthUI.IdpConfig.PhoneBuilder().build(), AuthUI.IdpConfig.GoogleBuilder().build())

    firebaseauth = FirebaseAuth.getInstance()
    listener =
        FirebaseAuth.AuthStateListener { myFirebaseAuth ->
            val user = myFirebaseAuth.currentUser
          if (user != null)
          {
              FirebaseMessaging.getInstance().token
                  .addOnCompleteListener {task ->

                      UserUtils.updateToken(this@SplashActivity, task.result)
                  }
              checkUserFromFirebase() }else showLoginLayout()
        }
  }

  private fun checkUserFromFirebase() {

    driverInfoReference
        .child(FirebaseAuth.getInstance().currentUser!!.uid)
        .addListenerForSingleValueEvent(
            object : ValueEventListener {
              override fun onDataChange(snapshot: DataSnapshot) {
                if (snapshot.exists()) {
//                  Toast.makeText(this@SplashActivity, "User already register", Toast.LENGTH_SHORT)
//                      .show()
                    val model = snapshot.getValue(DriverInfoModel::class.java)
                    goToHomeActivity(model)
                } else {
                  showRegisterLayout()
                }
              }

              override fun onCancelled(error: DatabaseError) {
                Toast.makeText(this@SplashActivity, error.message, Toast.LENGTH_SHORT).show()
              }
            })
  }

    private fun goToHomeActivity(model: DriverInfoModel?) {
        Common.currentUser = model
        startActivity(Intent(this,HomeActivity::class.java))
        finish()

    }

    private fun showRegisterLayout() {

    val builder = AlertDialog.Builder(this, R.style.DailogTheme)

    val itemView = LayoutInflater.from(this).inflate(R.layout.layout_register, null)

      val first_name = itemView.findViewById<View>(R.id.edt_first_name) as TextInputEditText
      val last_name = itemView.findViewById<View>(R.id.edt_last_name) as TextInputEditText
      val phone_number = itemView.findViewById<View>(R.id.edt_phone_number) as TextInputEditText

      val btn_continue = itemView.findViewById<View>(R.id.btn_register) as Button

      // set Data
    if (FirebaseAuth.getInstance().currentUser!!.phoneNumber != null &&
        !TextUtils.isDigitsOnly(FirebaseAuth.getInstance().currentUser!!.phoneNumber))
        phone_number.setText(FirebaseAuth.getInstance().currentUser!!.phoneNumber)

    // view
    builder.setView(itemView)
    val dialog = builder.create()
    dialog.show()

    // event
   btn_continue.setOnClickListener {
      if (TextUtils.isDigitsOnly(first_name.text.toString())) {
        Toast.makeText(this@SplashActivity, "Please Enter First Name", Toast.LENGTH_SHORT).show()
        return@setOnClickListener
      } else if (TextUtils.isDigitsOnly(last_name.text.toString())) {
        Toast.makeText(this@SplashActivity, "Please Enter Last Name", Toast.LENGTH_SHORT).show()
        return@setOnClickListener
//      } else if (TextUtils.isDigitsOnly(phone_number.text.toString())) {
//        Toast.makeText(this@SplashActivity, "Please Enter Phone Number", Toast.LENGTH_SHORT).show()
//        return@setOnClickListener
      } else {
        val model = DriverInfoModel()
        model.firstName = first_name.text.toString()
        model.lastName = last_name.text.toString()
        model.phoneNumber = phone_number.text.toString()
        model.rating = 0.0

        driverInfoReference
            .child(FirebaseAuth.getInstance().currentUser!!.uid)
            .setValue(model)
            .addOnFailureListener {
              Toast.makeText(this@SplashActivity, "" + it.message, Toast.LENGTH_SHORT).show()
              dialog.dismiss()

              progress_bar.visibility = View.GONE
            }
            .addOnSuccessListener {
              Toast.makeText(this@SplashActivity, "Register Successfully", Toast.LENGTH_SHORT)
                  .show()
              dialog.dismiss()

                goToHomeActivity(model)

              progress_bar.visibility = View.GONE
            }
      }
    }
  }

  private fun showLoginLayout() {

    val authMethodPickerLayout =
        AuthMethodPickerLayout.Builder(R.layout.layout_sign_in)
            .setPhoneButtonId(R.id.btn_phone_sign_in)
            .setGoogleButtonId(R.id.btn_google_sign_in)
            .build()

    startActivityForResult(
        AuthUI.getInstance()
            .createSignInIntentBuilder()
            .setAuthMethodPickerLayout(authMethodPickerLayout)
            .setTheme(R.style.LoginTheme)
            .setAvailableProviders(provider)
            .setIsSmartLockEnabled(false)
            .build(),
        LOGIN_REQUEST_CODE)
  }

  override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
    super.onActivityResult(requestCode, resultCode, data)
    if (requestCode == LOGIN_REQUEST_CODE) {
      val response = IdpResponse.fromResultIntent(data)
      if (resultCode == Activity.RESULT_OK) {
        val user = FirebaseAuth.getInstance().currentUser
      } else {
//        Toast.makeText(this@SplashActivity, "" + response!!.error!!.message, Toast.LENGTH_SHORT)
//            .show()
      }
    }
  }
}
