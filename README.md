# Project PDM — Group 06

## MySQL Database Connection Setup

Follow the instructions below to configure your local MySQL connection and run the Java application.

### Prerequisites

Before running the application, make sure you have:

- Java JDK installed.
- IntelliJ IDEA or another Java IDE.
- MySQL Server installed and running.
- The project cloned from this repository.

### Step 1: Check the Configuration Directory

After cloning the repository, navigate to:

`src/main/sources/`

If the `sources` directory already contains `application.properties.example`, **do not create another directory**. Use the existing directory.

If the directory does not exist, create `sources` inside `src/main/`.

### Step 2: Create `application.properties`

Inside `src/main/sources/`, create a new file named:

`application.properties`

If the repository already contains `application.properties.example`, copy that file and rename the copy to `application.properties`.

**Do not rename or delete `application.properties.example`.** It serves as a template for other team members.

### Step 3: Configure Your MySQL Credentials

Add the following configuration to `application.properties`:

```properties
db.url=jdbc:mysql://localhost:3306/online_examination
db.username=your_mysql_username
db.password=your_mysql_password
```

Replace `your_mysql_username` and `your_mysql_password` with your own MySQL username and password.

For example, if your MySQL username is `root`:

```properties
db.url=jdbc:mysql://localhost:3306/online_examination
db.username=root
db.password=your_actual_password
```

Do not include quotation marks around the values.

**Important:** Each team member should use their own local MySQL credentials. Do not share your password or commit `application.properties` to GitHub.

### Step 4: Prepare the Database

Make sure the database `online_examination` exists on your MySQL server.

If it does not exist, create it using MySQL Workbench or another MySQL client:

```sql
CREATE DATABASE online_examination;
```

This creates an empty database. If the application requires tables, those tables must also be created using the team's agreed database schema or SQL script.

### Step 5: Run the Application

1. Open the project in your IDE.
2. Reload the Maven project if necessary.
3. Run `Main.java`.
4. Check the console output.

If the connection is successful, you should see:

```text
Connected to MySQL successfully!
```

### Troubleshooting

- **`application.properties not found`:** Check that the file is in the configured resources directory and that Maven includes that directory in the build.
- **`Access denied for user`:** Check your MySQL username and password.
- **`Unknown database 'online_examination'`:** Create the database or verify the database name in the JDBC URL.
- **Connection refused:** Make sure MySQL Server is running and the host and port are correct.

### Security Reminder

- Do not commit `application.properties` because it may contain credentials.
- Keep `application.properties.example` in the repository as a configuration template.
- Make sure the actual configuration file is excluded in `.gitignore`.

### Team Notes

The current configuration uses a local MySQL server at `localhost:3306`. If the team later switches to a shared MySQL server or another database system, update the connection configuration and any required JDBC dependencies accordingly.
