# Development Guide

This guide provides instructions for setting up a development environment, building the project, and contributing to Coin of the Realm.

## Prerequisites

### Required Software

- **Java Development Kit (JDK)**: Version 21 or higher
- **Gradle**: Version 8.0 or higher (included via Gradle Wrapper)
- **Git**: For version control
- **IDE**: IntelliJ IDEA (recommended) or Eclipse

### Recommended Tools

- **Paper Server**: For testing the plugin
- **ServiceIO**: For testing banking features
- **Build Tools**: Gradle (included)

## Development Setup

### 1. Clone the Repository

```bash
git clone https://github.com/radawson/cotr.git
cd cotr
```

### 2. Import into IDE

#### IntelliJ IDEA

1. Open IntelliJ IDEA
2. Select "Open" or "Import Project"
3. Navigate to the cloned repository
4. Select the `build.gradle.kts` file
5. Choose "Open as Project"
6. Wait for Gradle to sync and download dependencies

#### Eclipse

1. Open Eclipse
2. File → Import → Gradle → Existing Gradle Project
3. Select the cloned repository directory
4. Click "Finish"
5. Wait for Gradle to build the project

### 3. Configure IDE Settings

#### IntelliJ IDEA

- **Java Version**: Ensure Project SDK is set to Java 21
- **Gradle JVM**: Set to Java 21 in Settings → Build, Execution, Deployment → Build Tools → Gradle
- **Code Style**: Use project code style (if available)

#### Eclipse

- **Java Version**: Set Java Build Path to Java 21
- **Gradle**: Use Gradle Wrapper (included)

### 4. Verify Setup

Run the build to verify everything is set up correctly:

```bash
./gradlew build
```

On Windows:
```bash
gradlew.bat build
```

If the build succeeds, your development environment is ready!

## Project Structure

```
cotr/
├── src/
│   ├── main/
│   │   ├── java/
│   │   │   └── org/clockworx/cotr/
│   │   │       ├── CoinOfTheRealmPlugin.java    # Main plugin class
│   │   │       ├── bank/                        # Banking system
│   │   │       │   ├── BankManager.java
│   │   │       │   ├── AccountMembershipManager.java
│   │   │       │   ├── BankReflectionHelper.java
│   │   │       │   ├── AccountRole.java
│   │   │       │   └── AccountMembership.java
│   │   │       ├── command/                     # Command handling
│   │   │       │   └── CotrCommand.java
│   │   │       ├── config/                      # Configuration
│   │   │       │   ├── ConfigManager.java
│   │   │       │   └── CoinConfig.java
│   │   │       ├── entity/                      # Entity management
│   │   │       │   └── CoinEntityManager.java
│   │   │       ├── item/                        # Coin item system
│   │   │       │   └── CoinItem.java
│   │   │       └── listener/                     # Event listeners
│   │   │           └── CoinListener.java
│   │   └── resources/
│   │       ├── plugin.yml                       # Plugin metadata
│   │       ├── paper-plugin.yml                 # Paper plugin metadata
│   │       └── config.yml                      # Default configuration
│   └── test/                                    # Test files (if any)
├── docs/                                        # Documentation
├── build.gradle.kts                             # Build configuration
├── gradle.properties                            # Gradle properties
├── gradlew                                      # Gradle wrapper (Unix)
├── gradlew.bat                                  # Gradle wrapper (Windows)
└── README.md                                    # Project readme
```

## Building the Project

### Build Commands

#### Build JAR

```bash
./gradlew build
```

This will:
- Compile the Java source code
- Process resources (plugin.yml, config.yml)
- Create the JAR file in `build/libs/`

#### Clean Build

```bash
./gradlew clean build
```

Removes previous build artifacts before building.

#### Build Without Tests

```bash
./gradlew build -x test
```

#### Create Shadow JAR (if configured)

```bash
./gradlew shadowJar
```

### Build Output

The compiled JAR file will be located at:
```
build/libs/CoinOfTheRealm-<version>.jar
```

### Version Management

The version is specified in `gradle.properties`:
```properties
version=0.2.1
```

Update this value before building a new release.

## Running and Testing

### Setting Up a Test Server

1. **Download Paper Server**:
   - Download Paper 1.21.11 or higher from [PaperMC](https://papermc.io/downloads)
   - Create a test server directory

2. **Install Dependencies**:
   - Place your built JAR in the `plugins` folder
   - (Optional) Install ServiceIO for banking feature testing

3. **Run the Server**:
   ```bash
   java -jar paper-1.21.11.jar
   ```

4. **Test the Plugin**:
   - Join the server
   - Test commands: `/cotr drop 1`, `/cotr balance`
   - Verify configuration files are created
   - Test banking features (if ServiceIO is installed)

### Debugging

#### IntelliJ IDEA

1. Create a new Run Configuration:
   - Type: "Application" or "Gradle"
   - Main class: Not applicable (use Gradle task)
   - Working directory: Project root

2. Or use Gradle run task:
   - Create a custom Gradle task to run Paper server
   - Or use a plugin like "Minecraft Development" for IntelliJ

#### Eclipse

1. Create a new Java Application run configuration
2. Or use external tools to run the server

### Testing Banking Features

To test banking features:

1. Install ServiceIO plugin in your test server
2. Ensure `banking.enabled` is `true` in `config.yml`
3. Test banking commands:
   - `/cotr deposit 100`
   - `/cotr withdraw 50`
   - `/cotr balance`
   - `/cotr account create test-account`

## Code Style and Guidelines

### Code Style

- Follow Java naming conventions
- Use meaningful variable and method names
- Add Javadoc comments for public APIs
- Keep methods focused and single-purpose
- Use `@NotNull` and `@Nullable` annotations where appropriate

### Package Structure

- `org.clockworx.cotr` - Main package
- `org.clockworx.cotr.bank` - Banking system
- `org.clockworx.cotr.command` - Command handling
- `org.clockworx.cotr.config` - Configuration
- `org.clockworx.cotr.entity` - Entity management
- `org.clockworx.cotr.item` - Coin item system
- `org.clockworx.cotr.listener` - Event listeners

### Documentation

- Add Javadoc comments for all public classes and methods
- Include parameter descriptions
- Document return values and exceptions
- Add usage examples where helpful

### Error Handling

- Use appropriate exception types
- Provide meaningful error messages
- Log errors with appropriate levels
- Handle null values gracefully

## Contributing

### Contribution Process

1. **Fork the Repository**:
   - Fork the repository on GitHub
   - Clone your fork locally

2. **Create a Branch**:
   ```bash
   git checkout -b feature/your-feature-name
   ```
   Or for bug fixes:
   ```bash
   git checkout -b fix/your-bug-fix
   ```

3. **Make Changes**:
   - Write your code following the code style guidelines
   - Add tests if applicable
   - Update documentation as needed

4. **Test Your Changes**:
   - Build the project: `./gradlew build`
   - Test in a local server
   - Verify no regressions

5. **Commit Your Changes**:
   ```bash
   git add .
   git commit -m "Description of your changes"
   ```
   Use clear, descriptive commit messages.

6. **Push to Your Fork**:
   ```bash
   git push origin feature/your-feature-name
   ```

7. **Create a Pull Request**:
   - Go to the original repository on GitHub
   - Click "New Pull Request"
   - Select your branch
   - Fill out the PR template (if available)
   - Submit the pull request

### Pull Request Guidelines

- **Clear Description**: Describe what changes you made and why
- **Reference Issues**: Link to related issues if applicable
- **Testing**: Describe how you tested your changes
- **Documentation**: Update documentation if needed
- **Code Quality**: Ensure code follows style guidelines

### What to Contribute

We welcome contributions in the following areas:

- **Bug Fixes**: Fix issues reported in GitHub Issues
- **Features**: New functionality (discuss in Issues first)
- **Documentation**: Improvements to existing docs or new docs
- **Code Quality**: Refactoring, performance improvements
- **Tests**: Unit tests, integration tests

### Before Submitting

- [ ] Code follows style guidelines
- [ ] All tests pass (if applicable)
- [ ] Documentation is updated
- [ ] Changes are tested in a local server
- [ ] Commit messages are clear and descriptive
- [ ] Pull request description is complete

## Dependencies

### Build Dependencies

The project uses the following build tools (defined in `build.gradle.kts`):

- **Gradle**: Build system (via wrapper)
- **Paperweight**: Paper API and development tools
- **Shadow**: JAR packaging (if configured)

### Runtime Dependencies

- **Paper API**: Minecraft server API (1.21.11)
- **ServiceIO**: Optional banking dependency (2.3.1)

### Dependency Management

Dependencies are managed in `build.gradle.kts`:

```kotlin
dependencies {
  compileOnly("io.papermc.paper:paper-api:1.21.11-R0.1-SNAPSHOT")
  compileOnly("net.thenextlvl.services:service-io:2.3.1")
  
  paperweight.paperDevBundle("1.21.11-R0.1-SNAPSHOT")
}
```

## Gradle Tasks

### Common Tasks

- `./gradlew build` - Build the project
- `./gradlew clean` - Clean build artifacts
- `./gradlew classes` - Compile classes only
- `./gradlew test` - Run tests (if any)
- `./gradlew javadoc` - Generate Javadoc

### Paper-Specific Tasks

- `./gradlew reobfJar` - Reobfuscate JAR (if using Paperweight)
- `./gradlew paperclip` - Build Paper server JAR (if configured)

## Troubleshooting

### Build Issues

**Problem**: Gradle sync fails  
**Solution**: 
- Check Java version: `java -version` (should be 21+)
- Clear Gradle cache: `./gradlew clean`
- Delete `.gradle` folder and retry

**Problem**: Dependencies not found  
**Solution**:
- Check internet connection
- Verify repository URLs in `build.gradle.kts`
- Clear Gradle cache

**Problem**: Compilation errors  
**Solution**:
- Check Java version matches project requirements
- Verify IDE is using correct JDK
- Clean and rebuild: `./gradlew clean build`

### Runtime Issues

**Problem**: Plugin doesn't load  
**Solution**:
- Check server version (1.21.11+)
- Verify Java version (21+)
- Check server logs for errors

**Problem**: Banking features not working  
**Solution**:
- Verify ServiceIO is installed
- Check `banking.enabled` in config.yml
- Review server logs for ServiceIO errors

## Development Resources

### Documentation

- [Paper API Documentation](https://docs.papermc.io/paper/)
- [ServiceIO Documentation](https://github.com/thenextlvl/service-io)
- [Gradle Documentation](https://docs.gradle.org/)

### Useful Links

- [PaperMC Discord](https://discord.gg/papermc) - Community support
- [GitHub Issues](https://github.com/radawson/cotr/issues) - Bug reports and feature requests
- [Project Wiki](https://github.com/radawson/cotr/wiki) - Additional documentation

## Release Process

### Preparing a Release

1. **Update Version**:
   - Update `version` in `gradle.properties`
   - Update version in `plugin.yml` (if needed)

2. **Update Changelog**:
   - Document all changes since last release
   - Include new features, bug fixes, breaking changes

3. **Build Release**:
   ```bash
   ./gradlew clean build
   ```

4. **Test Release**:
   - Test in a clean server environment
   - Verify all features work correctly
   - Test with and without ServiceIO

5. **Create Release**:
   - Create a new release on GitHub
   - Upload the JAR file
   - Include changelog in release notes
   - Tag the release

## License

This project is licensed under the terms specified in the LICENSE file. By contributing, you agree to license your contributions under the same license.

## Contact

For questions about development or contributions:

- **GitHub Issues**: [Create an issue](https://github.com/radawson/cotr/issues)
- **GitHub Discussions**: [Start a discussion](https://github.com/radawson/cotr/discussions)

## Related Documentation

- [API Documentation](API.md) - Developer API reference
- [Architecture Documentation](ARCHITECTURE.md) - System design
- [Configuration Reference](CONFIGURATION.md) - Configuration options
- [Installation Guide](INSTALLATION.md) - User installation instructions
