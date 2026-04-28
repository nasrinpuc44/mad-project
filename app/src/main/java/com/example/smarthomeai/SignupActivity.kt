package com.example.smarthomeai

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.auth.UserProfileChangeRequest

class SignupActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            SignupUI(
                onSignInClick = {
                    startActivity(Intent(this, LoginActivity::class.java))
                    finish()
                },
                onSignUpSuccess = {
                    startActivity(Intent(this, LoginActivity::class.java))
                    finish()
                }
            )
        }
    }
}

@Composable
fun signupFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedContainerColor   = Color.White,
    unfocusedContainerColor = Color.White,
    focusedBorderColor      = Color(0xFF2CF46F),
    unfocusedBorderColor    = Color(0xFFDDDDDD),
    focusedTextColor        = Color.Black,
    unfocusedTextColor      = Color.Black,
    cursorColor             = Color(0xFF008F8F),
    focusedPlaceholderColor = Color.Gray,
    unfocusedPlaceholderColor = Color.Gray,
    focusedLeadingIconColor = Color.Gray,
    unfocusedLeadingIconColor = Color.Gray
)

val fieldTextStyle = TextStyle(color = Color.Black, fontSize = 14.sp)
val fieldShape = RoundedCornerShape(50.dp)

@Composable
fun SignupUI(
    onSignInClick: () -> Unit = {},
    onSignUpSuccess: () -> Unit = {}
) {
    val context = LocalContext.current
    val auth = Firebase.auth
    var firstName by remember { mutableStateOf("") }
    var lastName by remember { mutableStateOf("") }
    var emailInput by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var showError by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var showVerificationNotice by remember { mutableStateOf(false) }

    val greenGradient = Brush.horizontalGradient(
        listOf(Color(0xFF2CF46F), Color(0xFF008F8F))
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF5F5F5))
            .verticalScroll(rememberScrollState())
    ) {

        // HEADER
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(220.dp)
                .background(
                    Brush.verticalGradient(
                        listOf(Color(0xFF2CF46F), Color(0xFF007A8A))
                    )
                ),
            contentAlignment = Alignment.TopCenter
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(top = 48.dp)
            ) {
                Text(
                    "CREATE ACCOUNT",
                    color = Color.White,
                    fontSize = 26.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 2.sp
                )
                Text(
                    "JOIN HOME CONTROL TODAY",
                    color = Color.White.copy(alpha = 0.85f),
                    fontSize = 10.sp,
                    letterSpacing = 1.5.sp
                )
                Spacer(modifier = Modifier.height(20.dp))
                Box(
                    modifier = Modifier
                        .size(72.dp)
                        .background(Color.White.copy(alpha = 0.2f), CircleShape)
                        .border(2.dp, Color.White.copy(alpha = 0.5f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.Person,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(36.dp)
                    )
                }
            }
        }

        // FORM
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 24.dp, bottom = 32.dp)
                .padding(horizontal = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {

            Spacer(modifier = Modifier.height(20.dp))

            // Verification Notice
            if (showVerificationNotice) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFE8F5E9)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            "✓ Verification Email Sent!",
                            color = Color(0xFF2E7D32),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            "Please check your inbox and verify your email address before signing in.",
                            color = Color(0xFF2E7D32),
                            fontSize = 12.sp,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Button(
                            onClick = { onSignInClick() },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF2CF46F),
                                contentColor = Color.Black
                            ),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Go to Sign In")
                        }
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
            }

            // Error Message
            if (showError) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFFFE5E5)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        errorMessage,
                        color = Color.Red,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(8.dp)
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
            }

            // Loading Indicator
            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(40.dp),
                    color = Color(0xFF2CF46F)
                )
                Spacer(modifier = Modifier.height(8.dp))
            }

            if (!showVerificationNotice) {
                // FULL NAME
                SectionLabel("Full Name")
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedTextField(
                        value = firstName,
                        onValueChange = { firstName = it },
                        placeholder = { Text("First name", fontSize = 13.sp) },
                        leadingIcon = {
                            Icon(
                                Icons.Default.Person,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                        },
                        textStyle = fieldTextStyle,
                        shape = fieldShape,
                        colors = signupFieldColors(),
                        singleLine = true,
                        modifier = Modifier
                            .weight(1f)
                            .height(56.dp),
                        enabled = !isLoading
                    )
                    OutlinedTextField(
                        value = lastName,
                        onValueChange = { lastName = it },
                        placeholder = { Text("Last name", fontSize = 13.sp) },
                        textStyle = fieldTextStyle,
                        shape = fieldShape,
                        colors = signupFieldColors(),
                        singleLine = true,
                        modifier = Modifier
                            .weight(1f)
                            .height(56.dp),
                        enabled = !isLoading
                    )
                }

                Spacer(modifier = Modifier.height(18.dp))

                // EMAIL INPUT
                SectionLabel("Email Address")
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = emailInput,
                    onValueChange = { emailInput = it },
                    placeholder = { Text("Gmail address") },
                    leadingIcon = { Icon(Icons.Default.Email, contentDescription = null) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                    textStyle = fieldTextStyle,
                    shape = fieldShape,
                    colors = signupFieldColors(),
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    enabled = !isLoading
                )

                Spacer(modifier = Modifier.height(18.dp))

                // PASSWORD
                SectionLabel("Password")
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    placeholder = { Text("Create password") },
                    leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null) },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    textStyle = fieldTextStyle,
                    shape = fieldShape,
                    colors = signupFieldColors(),
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    enabled = !isLoading
                )

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = confirmPassword,
                    onValueChange = { confirmPassword = it },
                    placeholder = { Text("Confirm password") },
                    leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null) },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    textStyle = fieldTextStyle,
                    shape = fieldShape,
                    colors = signupFieldColors(),
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    enabled = !isLoading
                )

                Spacer(modifier = Modifier.height(28.dp))

                // CREATE ACCOUNT BUTTON
                Button(
                    onClick = {
                        when {
                            firstName.isBlank() || lastName.isBlank() -> {
                                showError = true
                                errorMessage = "Please enter your full name"
                            }
                            emailInput.isBlank() -> {
                                showError = true
                                errorMessage = "Please enter your email address"
                            }
                            password.isBlank() -> {
                                showError = true
                                errorMessage = "Please enter a password"
                            }
                            password != confirmPassword -> {
                                showError = true
                                errorMessage = "Passwords do not match"
                            }
                            password.length < 6 -> {
                                showError = true
                                errorMessage = "Password must be at least 6 characters"
                            }
                            else -> {
                                showError = false
                                isLoading = true

                                val fullName = "$firstName $lastName"

                                // Email/Password Sign Up with Verification
                                auth.createUserWithEmailAndPassword(emailInput, password)
                                    .addOnCompleteListener { task ->
                                        if (task.isSuccessful) {
                                            val user = task.result?.user

                                            // Update display name
                                            val profileUpdates = UserProfileChangeRequest.Builder()
                                                .setDisplayName(fullName)
                                                .build()

                                            user?.updateProfile(profileUpdates)?.addOnCompleteListener { updateTask ->
                                                // Send email verification
                                                user?.sendEmailVerification()?.addOnCompleteListener { verifyTask ->
                                                    isLoading = false
                                                    if (verifyTask.isSuccessful) {
                                                        // Sign out because email not verified yet
                                                        auth.signOut()
                                                        showVerificationNotice = true
                                                    } else {
                                                        showError = true
                                                        errorMessage = verifyTask.exception?.message ?: "Failed to send verification email"
                                                    }
                                                } ?: run {
                                                    isLoading = false
                                                    showError = true
                                                    errorMessage = "Failed to send verification email"
                                                }
                                            } ?: run {
                                                isLoading = false
                                                showError = true
                                                errorMessage = "Failed to update profile"
                                            }
                                        } else {
                                            isLoading = false
                                            showError = true
                                            errorMessage = task.exception?.message ?: "Sign up failed"
                                        }
                                    }
                            }
                        }
                    },
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
                    contentPadding = PaddingValues(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp),
                    enabled = !isLoading
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(greenGradient, RoundedCornerShape(16.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            if (isLoading) "Creating Account..." else "Create Account",
                            color = Color.Black,
                            fontWeight = FontWeight.Bold,
                            fontSize = 17.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // TERMS
                Text(
                    buildAnnotatedString {
                        append("By signing up you agree to our ")
                        withStyle(SpanStyle(color = Color(0xFF008F8F), fontWeight = FontWeight.SemiBold)) {
                            append("Terms")
                        }
                        append(" & ")
                        withStyle(SpanStyle(color = Color(0xFF008F8F), fontWeight = FontWeight.SemiBold)) {
                            append("Privacy Policy")
                        }
                    },
                    fontSize = 11.sp,
                    color = Color.Gray,
                    lineHeight = 16.sp
                )

                Spacer(modifier = Modifier.height(12.dp))

                // SIGN IN ROW
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Already have an account? ", color = Color.Gray, fontSize = 13.sp)
                    TextButton(
                        onClick = { if (!isLoading) onSignInClick() }
                    ) {
                        Text(
                            "Sign In",
                            color = Color(0xFF008F8F),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun SectionLabel(text: String) {
    Text(
        text.uppercase(),
        fontSize = 11.sp,
        color = Color.Gray,
        fontWeight = FontWeight.Bold,
        letterSpacing = 1.sp,
        modifier = Modifier.fillMaxWidth()
    )
}