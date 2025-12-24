package com.upgovt.ashadiary.presentation.ui

import android.os.Bundle
import android.view.MenuItem
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import androidx.fragment.app.Fragment
import com.google.android.material.navigation.NavigationView
import com.upgovt.ashadiary.R
import com.upgovt.ashadiary.presentation.ui.dashboard.DashboardFragment
import com.upgovt.ashadiary.presentation.ui.registration.BeneficiaryRegistrationFragment

/**
 * MainActivity - Main navigation container
 * Navigation drawer with fragments for different features
 */
class MainActivity : AppCompatActivity(), NavigationView.OnNavigationItemSelectedListener {
    
    private lateinit var drawerLayout: DrawerLayout
    private lateinit var navigationView: NavigationView
    private lateinit var toolbar: Toolbar
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        
        setupToolbar()
        setupNavigationDrawer()
        
        // Show dashboard by default
        if (savedInstanceState == null) {
            loadFragment(DashboardFragment())
            navigationView.setCheckedItem(R.id.nav_dashboard)
        }
    }
    
    private fun setupToolbar() {
        toolbar = findViewById(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
    }
    
    private fun setupNavigationDrawer() {
        drawerLayout = findViewById(R.id.drawer_layout)
        navigationView = findViewById(R.id.nav_view)
        
        val toggle = ActionBarDrawerToggle(
            this,
            drawerLayout,
            toolbar,
            R.string.navigation_drawer_open,
            R.string.navigation_drawer_close
        )
        drawerLayout.addDrawerListener(toggle)
        toggle.syncState()
        
        navigationView.setNavigationItemSelectedListener(this)
    }
    
    override fun onNavigationItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.nav_dashboard -> {
                loadFragment(DashboardFragment())
            }
            R.id.nav_register_beneficiary -> {
                loadFragment(BeneficiaryRegistrationFragment())
            }
            R.id.nav_search -> {
                // TODO: SearchFragment
            }
            R.id.nav_record_visit -> {
                // TODO: VisitTypeSelectionFragment
            }
            R.id.nav_edit_requests -> {
                // TODO: EditRequestListFragment
            }
            R.id.nav_duplicates -> {
                // TODO: DuplicateReviewFragment
            }
            R.id.nav_sync -> {
                // Trigger manual sync
                // TODO: Call SyncViewModel.triggerSync()
            }
            R.id.nav_logout -> {
                // Clear session and return to login
                finish()
            }
        }
        
        drawerLayout.closeDrawer(GravityCompat.START)
        return true
    }
    
    private fun loadFragment(fragment: Fragment) {
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, fragment)
            .commit()
    }
    
    override fun onBackPressed() {
        if (drawerLayout.isDrawerOpen(GravityCompat.START)) {
            drawerLayout.closeDrawer(GravityCompat.START)
        } else {
            super.onBackPressed()
        }
    }
}
