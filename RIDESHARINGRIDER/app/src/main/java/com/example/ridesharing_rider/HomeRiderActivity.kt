package com.example.ridesharing_rider

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.text.TextUtils
import android.view.Menu
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.navigation.NavigationView
import androidx.navigation.findNavController
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.navigateUp
import androidx.navigation.ui.setupActionBarWithNavController
import androidx.navigation.ui.setupWithNavController
import androidx.drawerlayout.widget.DrawerLayout
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.navigation.NavController
import com.bumptech.glide.Glide
import com.example.ridesharing_rider.databinding.ActivityHomeRiderBinding
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.StorageReference
import java.lang.StringBuilder

class HomeRiderActivity : AppCompatActivity() {

    private lateinit var appBarConfiguration: AppBarConfiguration
    private lateinit var binding: ActivityHomeRiderBinding
    private lateinit var navView: NavigationView
    private lateinit var drawerLayout: DrawerLayout
    private lateinit var navController: NavController
    private lateinit var waitingDialog: AlertDialog
    private lateinit var img_avatar: ImageView
    private lateinit var storageReference: StorageReference
    private var imageUri: Uri? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityHomeRiderBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.appBarHomeRider.toolbar)


        drawerLayout = binding.drawerLayout
         navView= binding.navView
         navController = findNavController(R.id.nav_host_fragment_content_home_rider)
        // Passing each menu ID as a set of Ids because each
        // menu should be considered as top level destinations.
        appBarConfiguration = AppBarConfiguration(
            setOf(
                R.id.nav_home
            ), drawerLayout
        )
        setupActionBarWithNavController(navController, appBarConfiguration)
        navView.setupWithNavController(navController)
        init()
    }

    private fun init(){
        storageReference = FirebaseStorage.getInstance().getReference()

        waitingDialog = AlertDialog.Builder(this).setMessage("Waiting...").setCancelable(false).create()

        navView.setNavigationItemSelectedListener {
            if (it.itemId == R.id.nav_sign_out) {
                val builder = AlertDialog.Builder(this@HomeRiderActivity)
                builder
                    .setTitle("Sign out")
                    .setMessage("Do you really want to sign out")
                    .setNegativeButton("Cancel") { dialogInterface, _ -> dialogInterface.dismiss() }
                    .setPositiveButton(
                        "SIGN OUT")
                        { dialogInterface, _ ->
                            FirebaseAuth.getInstance().signOut()
                            val intent = Intent(this@HomeRiderActivity, SplashActivity::class.java)
                            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                            startActivity(intent)
                            finish()
                        }
                    .setCancelable(false)

                val dialog = builder.create()
                dialog.setOnShowListener {
                    dialog
                        .getButton(AlertDialog.BUTTON_POSITIVE)
                        .setTextColor(ContextCompat.getColor(this@HomeRiderActivity,android.R.color.holo_red_dark))
                    dialog
                        .getButton(AlertDialog.BUTTON_NEGATIVE)
                        .setTextColor(ContextCompat.getColor(this@HomeRiderActivity,R.color.colorAccent))
                }
                dialog.show()
            }
            true
        }

        val headerView = navView.getHeaderView(0)
        val txt_Name = headerView.findViewById<View>(R.id.txt_name) as TextView
        val txt_Phone = headerView.findViewById<View>(R.id.txt_phone) as TextView

        img_avatar = headerView.findViewById<View>(R.id.img_avatar) as ImageView
        txt_Name.setText(Common.buildWelcomeMessage())
        txt_Phone.setText(Common.currentRider?.phoneNumber)

        if (Common.currentRider != null &&
            Common.currentRider!!.avatar != null &&
            !TextUtils.isEmpty(Common.currentRider!!.avatar)) {

            Glide.with(this).load(Common.currentRider!!.avatar).into(img_avatar)
        }
        img_avatar.setOnClickListener {
            val intent = Intent()
            intent.setType("image/*")
            intent.setAction(Intent.ACTION_GET_CONTENT)

            startActivityForResult(Intent.createChooser(intent, "Select Picture"), PICK_IMAGE_REQUEST)
        }
    }
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == PICK_IMAGE_REQUEST && resultCode == Activity.RESULT_OK) {
            if (data != null && data.data != null) {
                imageUri = data.data
                img_avatar.setImageURI(imageUri)

                showDialogUpload()
            }
        }
    }

    private fun showDialogUpload() {

        val builder = AlertDialog.Builder(this@HomeRiderActivity)
        builder
            .setTitle("change Avatar")
            .setMessage("Do you really want to change Avatar")
            .setNegativeButton("Cancel") { dialogInterface, _ -> dialogInterface.dismiss() }
            .setPositiveButton("CHANGE") { dialogInterface, _ ->
                if (imageUri != null) {
                    waitingDialog.show()
                    val avatarFolder =
                        storageReference.child("avatar/" + FirebaseAuth.getInstance().currentUser!!.uid)

                    avatarFolder
                        .putFile(imageUri!!)
                        .addOnFailureListener {
                            Snackbar.make(drawerLayout, it.message!!, Snackbar.LENGTH_LONG).show()
                            waitingDialog.dismiss()
                        }
                        .addOnCompleteListener {
                            if (it.isSuccessful){
                                avatarFolder.downloadUrl.addOnSuccessListener {
                                    val update_data = HashMap<String,Any>()

                                    UserUtils.updateUser(drawerLayout,update_data)

                                }
                            }
                            waitingDialog.dismiss()
                        }.addOnProgressListener {
                            val progress = (100.0*it.bytesTransferred/it.totalByteCount)
                            waitingDialog.setMessage(StringBuilder("Uploading: ").append(progress).append("%"))
                        }
                }
            }
            .setCancelable(false)

        val dialog = builder.create()
        dialog.setOnShowListener {
            dialog
                .getButton(AlertDialog.BUTTON_POSITIVE)
                .setTextColor(ContextCompat.getColor(this@HomeRiderActivity,android.R.color.holo_red_dark))
            dialog
                .getButton(AlertDialog.BUTTON_NEGATIVE)
                .setTextColor(ContextCompat.getColor(this@HomeRiderActivity,R.color.colorAccent))
        }
        dialog.show()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        // Inflate the menu; this adds items to the action bar if it is present.
        menuInflater.inflate(R.menu.home_rider, menu)
        return true
    }

    override fun onSupportNavigateUp(): Boolean {
        val navController = findNavController(R.id.nav_host_fragment_content_home_rider)
        return navController.navigateUp(appBarConfiguration) || super.onSupportNavigateUp()
    }

    companion object {
        val PICK_IMAGE_REQUEST = 7272
    }
}