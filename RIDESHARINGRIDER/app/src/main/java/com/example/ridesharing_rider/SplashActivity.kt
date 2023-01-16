package com.example.ridesharing_rider

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.text.TextUtils
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import android.widget.Toast
import com.example.ridesharing_rider.Model.DriverInfoModel
import com.example.ridesharing_rider.Model.RiderModel
import com.firebase.ui.auth.AuthMethodPickerLayout
import com.firebase.ui.auth.AuthUI
import com.firebase.ui.auth.IdpResponse
import com.google.android.material.textfield.TextInputEditText
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*
import com.google.firebase.messaging.FirebaseMessaging
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.core.Completable
import kotlinx.android.synthetic.main.activity_splash.*
import java.util.*
import java.util.concurrent.TimeUnit

class SplashActivity : AppCompatActivity() {

    companion object {
        private val LOGIN_REQUEST_CODE = 7171
    }

    private lateinit var provider: List<AuthUI.IdpConfig>
    private lateinit var firebaseauth: FirebaseAuth
    private lateinit var listener: FirebaseAuth.AuthStateListener

    private lateinit var database: FirebaseDatabase
    private lateinit var riderInfoReference: DatabaseReference

    override fun onStart() {
        super.onStart()
        delaySplashScreen()
    }

    private fun delaySplashScreen() {
        Completable.timer(1, TimeUnit.SECONDS, AndroidSchedulers.mainThread()).subscribe {
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
        riderInfoReference = database.getReference(Common.RIDER_INFO_REFERENCE)
        provider =
            Arrays.asList(
                AuthUI.IdpConfig.PhoneBuilder().build(), AuthUI.IdpConfig.GoogleBuilder().build())

        firebaseauth = FirebaseAuth.getInstance()
        listener =
            FirebaseAuth.AuthStateListener {
                val user = it.currentUser
                if (user != null)
                {
                    FirebaseMessaging.getInstance().token
                        .addOnCompleteListener {task ->
                            Log.d("qwerty", task.result.toString())
                            UserUtils.updateToken(this@SplashActivity, task.result)
                        }
                    checkUserFromFirebase() }else showLoginLayout()
            }
    }

    private fun checkUserFromFirebase() {

        riderInfoReference
            .child(FirebaseAuth.getInstance().currentUser!!.uid)
            .addListenerForSingleValueEvent(
                object : ValueEventListener {
                    override fun onDataChange(snapshot: DataSnapshot) {
                        if (snapshot.exists()) {
//                  Toast.makeText(this@SplashActivity, "User already register", Toast.LENGTH_SHORT)
//                      .show()
                            val model = snapshot.getValue(RiderModel::class.java)
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
    private fun goToHomeActivity(model: RiderModel?) {
        Common.currentRider = model
        startActivity(Intent(this,HomeRiderActivity::class.java))
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
                val model = RiderModel()
                model.firstName = first_name.text.toString()
                model.lastName = last_name.text.toString()
                model.phoneNumber = phone_number.text.toString()

                riderInfoReference
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


    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == LOGIN_REQUEST_CODE){
            val response = IdpResponse.fromResultIntent(data)
            if (resultCode == Activity.RESULT_OK)
            {
                val user = FirebaseAuth.getInstance().currentUser
            }
            else
            {
                Toast.makeText(this@SplashActivity,response!!.error!!.message,Toast.LENGTH_SHORT).show()
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

}