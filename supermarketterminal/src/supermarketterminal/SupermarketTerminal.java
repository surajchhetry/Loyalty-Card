package supermarketterminal;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.security.NoSuchAlgorithmException;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.InvalidKeySpecException;

import common.CONSTANTS;
import common.CLI;
import common.KeyManager;
import common.AppletCommunication;
import common.AppletSession;
import common.Formatter;
import common.Response;
import common.TerminalCrypto;


/** 
 * This class represents the terminal used at the
 * cash register in the supermarket
 * @author Geert Smelt
 * @author	Robin Oostrum
*/
public class SupermarketTerminal {
	
	/** communication gateway */
	AppletCommunication com;

	/** communication session */
	AppletSession session;

	/** Cryptographic functions */
	TerminalCrypto crypto;

	/** Supermarket public key */
	RSAPublicKey supermarketPublicKey;

	/** Supermarket private key */
	RSAPrivateKey supermarketPrivKey;

	/** keys directory */
	String keyDir = "./keys/";

	/** Current card id */
	int cardId;
	
	/** Current cash register id */
	int cashRegisterId;
	
	public SupermarketTerminal (int cashRegisterId) {
		this.cashRegisterId = cashRegisterId;
		System.out.println("Welcome to cash register " + cashRegisterId);
		loadKeyFiles();
		
		session = new AppletSession(supermarketPrivKey);
		com = new AppletCommunication(session);
		crypto = new TerminalCrypto();
		
		while (true) {
			main();
			System.out.print("\nPress return when card has been inserted.");
			waitForInput();
			waitToTryAgain();
		}
	}
	
	private void loadKeyFiles() {
		try {
			supermarketPrivKey = (RSAPrivateKey) KeyManager.loadKeyPair("supermarket")
					.getPrivate();
			supermarketPublicKey = (RSAPublicKey) KeyManager.loadKeyPair("supermarket")
					.getPublic();
		} catch (NoSuchAlgorithmException e) {
			e.printStackTrace();
		} catch (InvalidKeySpecException e) {
			e.printStackTrace();
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	
	/**
 	 * Terminal in the supermarket without a fancy GUI;
	 * moreover, we assume the rest of the authentication
	 * (a customer showing its ID card) happens correctly.
	 */
	private void main() {
		// wait until a card is inserted
		com.waitForCard();
		// authenticate the card and the terminal
		System.out.println("Authenticating card...");
		if (!session.authenticate(CONSTANTS.NAME_TERM)) {
			System.err.println("Authentication error.");
			return;
		}
		cardId = session.getCardIdAsInt();
		
		System.out.println("Succesfully authenticated card " + cardId);
		
		mainmenu: while (true) {
			String command = "0";
			try {
				Thread.sleep(1000);
			} catch (InterruptedException e) {
				System.err.println("Please do not interrupt me!");
			}
			
			command = CLI.prompt("1: add credits to card | " +
					"2: remove credits from card | 3: view balance | 9: exit \n");
			
			if (Integer.parseInt(command) == 1) {
				String addcredits = "";
				
				while (true) {
					String correct = "";
					addcredits = CLI.prompt("Please enter the amount of " +
							"credits to be added to the customer's card: ");
					
					CLI.showln("Credits to be added: " + addcredits);
					while (!correct.equals("N") && !correct.equals("Y")
							&& !correct.equals("C")) {
						correct = CLI
								.prompt("Is this correct? (Y)es/(N)o/(C)ancel: ");
					}
		
					if (correct.equals("C")) {
						continue mainmenu;
					} else if (correct.equals("Y")) {
						break;
					}
				}
				
				try {
					Short c = Short.parseShort(addcredits);
				} catch (Exception e) {
					System.out.println("Please insert a valid amount of credits between 0 and " + CONSTANTS.CREDITS_MAX);
				}
				writeCredits(Short.parseShort(addcredits));
			} else if (Integer.parseInt(command) == 2) {
				String removecredits = "";
				
				while (true) {
					String correct = "";
					removecredits = CLI.prompt("Please enter the amount of " +
							"credits to be removed from the customer's card: ");
					
					CLI.showln("Credits to be removed: " + removecredits);
					while (!correct.equals("N") && !correct.equals("Y")
							&& !correct.equals("C")) {
						correct = CLI
								.prompt("Is this correct? (Y)es/(N)o/(C)ancel: ");
					}
		
					if (correct.equals("C")) {
						continue mainmenu;
					} else if (correct.equals("Y")) {
						break;
					}
				}
				
				try {
					Short c = Short.parseShort(removecredits);
				} catch (Exception e) {
					System.out.println("Please insert a valid amount of credits between 0 and " + CONSTANTS.CREDITS_MAX);
				}
				removeCredits(Short.parseShort(removecredits));
				
			} else if (Integer.parseInt(command) == 3) {	
				getCredits();
			}
			else if (Integer.parseInt(command) == 9) {
				/* Exit program */
				break;
			} else {
				System.err.println("Incorrect command entered.");
			}
		}
		
	}
	
	/**
	 * Send the "check balance" instruction to the card
	 */
	private void getCredits() {
		if (!session.isAuthenticated()) {
			throw new SecurityException(
					"Cannot view balance, card not authenticated.");
		}
		Response resp = com.sendCommand(CONSTANTS.INS_BAL_CHECK);
		if (resp == null) {
			throw new SecurityException("Cannot view balance, card removed.");
		}
		if (!resp.success()) {
			throw new SecurityException("Error checking balance.");
		}

		short b = (short) Formatter.byteArrayToShort(resp.getData());
		System.out.println("Balance: " + b);
	}

	/**
	 * Send the "decrease balance" instruction to the card
	 */
	private void removeCredits(short credits) {
		if (!session.isAuthenticated()) {
			throw new SecurityException(
					"Cannot remove credits, card not authenticated.");
		}
		byte[] creditsData = Formatter.toByteArray(credits);

		Response resp = com.sendCommand(CONSTANTS.INS_BAL_DEC,
				creditsData);
		if (resp == null) {
			throw new SecurityException("Cannot remove credits, card removed.");
		}
		if (!resp.success()) {
			throw new SecurityException("Error removing credits.");
		}
		System.out.println("Credits removed from balance: " + credits);
	}

	/**
	 * Send the "increase balance" instruction to the card
	 */
	private void writeCredits(short credits) {
		if (!session.isAuthenticated()) {
			throw new SecurityException(
					"Cannot add credits, card not authenticated.");
		}

		byte[] creditsData = Formatter.toByteArray(credits);

		Response resp = com.sendCommand(CONSTANTS.INS_BAL_INC,
				creditsData);
		if (resp == null) {
			throw new SecurityException("Cannot add credits, card removed.");
		}
		if (!resp.success()) {
			throw new SecurityException("Error adding credits.");
		}
		System.out.println("Credits added to balance: " + credits);
	}

	/**
	 * Waits for the user to press any key
	 */
	private void waitForInput() {
		try {
			System.in.read();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	/**
	 * Waits 5 seconds
	 */
	private void waitToTryAgain() {
		sleep(1);
		System.out.print("Try to authenticate card again in ");
		for (int i = 5; i > 0; i--) {
			System.out.print(i + "... ");
			sleep(1);
		}
		System.out.println();
	}

	/**
	 * Sleep for x seconds
	 */
	private void sleep(int x) {
		try {
			Thread.sleep(1000 * x);
		} catch (InterruptedException e) {
			System.err.println("Session interrupted!");
		}
	}

	/**
	 * Start up the supermarket terminal
	 * 
	 * @param arg
	 */
	public static void main(String[] arg) {
		// Change integer to x to represent another cash register with ID x
		new SupermarketTerminal(1);
	}
}