/**
 * Copyright (c) 2005-2015, KoLmafia development team
 * http://kolmafia.sourceforge.net/
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 *
 *  [1] Redistributions of source code must retain the above copyright
 *      notice, this list of conditions and the following disclaimer.
 *  [2] Redistributions in binary form must reproduce the above copyright
 *      notice, this list of conditions and the following disclaimer in
 *      the documentation and/or other materials provided with the
 *      distribution.
 *  [3] Neither the name "KoLmafia" nor the names of its contributors may
 *      be used to endorse or promote products derived from this software
 *      without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
 * "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
 * LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS
 * FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE
 * COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT,
 * INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING,
 * BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER
 * CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT
 * LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN
 * ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 */

package net.sourceforge.kolmafia.request;

import java.util.List;
import java.util.Set;
import java.util.TreeMap;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import net.sourceforge.kolmafia.AdventureResult;
import net.sourceforge.kolmafia.KoLConstants.MafiaState;
import net.sourceforge.kolmafia.KoLConstants.Stat;
import net.sourceforge.kolmafia.KoLmafia;
import net.sourceforge.kolmafia.RequestLogger;

import net.sourceforge.kolmafia.objectpool.EffectPool;
import net.sourceforge.kolmafia.objectpool.ItemPool;

import net.sourceforge.kolmafia.persistence.MonsterDatabase.Phylum;

import net.sourceforge.kolmafia.preferences.Preferences;

import net.sourceforge.kolmafia.session.ChoiceManager;
import net.sourceforge.kolmafia.session.InventoryManager;

import net.sourceforge.kolmafia.utilities.StringUtilities;

public class DeckOfEveryCardRequest
	extends GenericRequest
{
	private static final TreeMap<Integer,EveryCard> idToCard = new TreeMap<Integer,EveryCard>();
	private static final TreeMap<String,EveryCard> canonicalNameToCard = new TreeMap<String,EveryCard>();
	private static final TreeMap<Phylum,EveryCard> phylumToCard = new TreeMap<Phylum,EveryCard>();
	private static final TreeMap<Stat,EveryCard> statToCard = new TreeMap<Stat,EveryCard>();
	private static final TreeMap<AdventureResult,EveryCard> buffToCard = new TreeMap<AdventureResult,EveryCard>();

	private static final AdventureResult STRONGLY_MOTIVATED = EffectPool.get( EffectPool.STRONGLY_MOTIVATED, 20 );
	private static final AdventureResult MAGICIANSHIP = EffectPool.get( EffectPool.MAGICIANSHIP, 20 );
	private static final AdventureResult DANCIN_FOOL = EffectPool.get( EffectPool.DANCIN_FOOL_CARD, 20 );
	private static final AdventureResult FORTUNE_OF_THE_WHEEL = EffectPool.get( EffectPool.FORTUNE_OF_THE_WHEEL, 20 );
	private static final AdventureResult RACING = EffectPool.get( EffectPool.RACING, 20 );

	static
	{
		registerCard( 1, "X of Clubs" );		// gives X seal-clubbing clubs, and 3 PvP fights
		registerCard( 3, "X of Diamonds" );		// gives X hyper-cubic zirconiae
		registerCard( 2, "X of Hearts" );		// gives X bubblegum hearts
		registerCard( 4, "X of Spades" );		// gives X grave-robbing shovels, and X letters of a string
		registerCard( 42, "X of Papayas" );		// gives X papayas
		registerCard( 65, "X of Kumquats" );		// gives X kumquats
		registerCard( 43, "X of Salads" );		// gives X delicious salads
		registerCard( 5, "X of Cups" );			// gives X random boozes
		registerCard( 8, "X of Coins" );		// gives X valuable coins
		registerCard( 7, "X of Swords" );		// gives X swords
		registerCard( 6, "X of Wands" );		// gives 5 turns of X random buffs.
		registerCard( 47, "XVI - The Tower" );		// Gives a random hero tower key
		registerCard( 66, "Professor Plum" );		// Get 10 plums
		registerCard( 59, "Spare Tire" );		// Get a tires
		registerCard( 60, "Extra Tank" );		// Get a full meat tank
		registerCard( 61, "Sheep" );			// Get 3 stone wool
		registerCard( 62, "Year of Plenty" );		// Get 5 random foods.
		registerCard( 63, "Mine" );			// Get one each of asbestos ore, linoleum ore, and chrome ore
		registerCard( 64, "Laboratory" );		// Get five random potions

		// The following give new items
		registerCard( 31, "Plains" );			// Gives a white mana
		registerCard( 32, "Swamp" );			// Gives a black mana
		registerCard( 33, "Mountain" );			// Gives a red mana
		registerCard( 34, "Forest" );			// Gives a green mana
		registerCard( 35, "Island" );			// Gives a blue mana
		registerCard( 52, "Lead Pipe" );		// Get a Lead Pipe
		registerCard( 53, "Rope" );			// Get a Rope
		registerCard( 54, "Wrench" );			// Get a Wrench
		registerCard( 55, "Candlestick" );		// Get a Candlestick
		registerCard( 56, "Knife" );			// Get a Knife
		registerCard( 57, "Revolver" );			// Get a Revolver
		registerCard( 41, "Gift Card" );		// Get a Gift Card
		registerCard( 58, "1952 Mickey Mantle" );	// Get a 1952 Mickey Mantle card

		// The following give stats
		registerCard( 68, "XXI - The World", Stat.MUSCLE );		// Gives 500 muscle substats
		registerCard( 70, "III - The Empress", Stat.MYSTICALITY );	// Gives 500 mysticality substats
		registerCard( 69, "VI - The Lovers", Stat.MOXIE );		// Gives 500 moxie substats

		// The following give skills
		registerCard( 36, "Healing Salve" );		// Gives the skill Healing Salve
		registerCard( 37, "Dark Ritual" );		// Gives the skill Dark Ritual
		registerCard( 38, "Lightning Bolt" );		// Gives the skill Lightning Bolt
		registerCard( 39, "Giant Growth" );		// Gives the skill Giant Growth
		registerCard( 40, "Ancestral Recall" );		// Gives the skill Ancestral Recall

		// The following give buffs
		registerCard( 51, "XI - Strength", STRONGLY_MOTIVATED );	// Gives 20 turns of Strongly Motivated (+200% muscle)
		registerCard( 50, "I - The Magician", MAGICIANSHIP );		// Gives 20 turns of Magicianship (+200% mysticality)
		registerCard( 49, "0 - The Fool", DANCIN_FOOL );		// Gives 20 turns of Dancin' Fool (+200% moxie)
		registerCard( 67, "X - The Wheel of Fortune", FORTUNE_OF_THE_WHEEL );	// Gives 20 turns of Fortune of the Wheel (+100% item drop)
		registerCard( 48, "The Race Card", RACING );			// Gives 20 turns of Racing! (+200% init)

		// The following lead to fights
		registerCard( 46, "Green Card" );		// Fight a legal alien
		registerCard( 45, "IV - The Emperor" );		// Fight The Emperor (drops The Emperor's dry cleaning)
		registerCard( 44, "IX - The Hermit" );		// Fight The Hermit

		registerCard( 15, "Werewolf", Phylum.BEAST );			// Fight a random Beast
		registerCard( 11, "The Hive", Phylum.BUG );			// Fight a random Bug
		registerCard( 26, "XVII - The Star", Phylum.CONSTELLATION );	// Fight a random Constellation
		registerCard( 18, "VII - The Chariot", Phylum.CONSTRUCT );	// Fight a random Construct
		registerCard( 16, "XV - The Devil", Phylum.DEMON );		// Fight a random Demon
		registerCard( 13, "V - The Hierophant", Phylum.DUDE );		// Fight a random Dude
		registerCard( 17, "Fire Elemental", Phylum.ELEMENTAL );		// Fight a random Elemental
		registerCard( 28, "Christmas Card", Phylum.ELF );		// Fight a random Elf
		registerCard( 29, "Go Fish", Phylum.FISH );			// Fight a random Fish
		registerCard( 10, "Goblin Sapper", Phylum.GOBLIN );		// Fight a random Goblin
		registerCard( 20, "II - The High Priestess", Phylum.HIPPY );	// Fight a random Hippy
		registerCard( 24, "XIV - Temperance", Phylum.HOBO );		// Fight a random Hobo
		registerCard( 14, "XVIII - The Moon", Phylum.HORROR );		// Fight a random Horror
		registerCard( 12, "Hunky Fireman Card", Phylum.HUMANOID );	// Fight a random Humanoid
		registerCard( 30, "Aquarius Horoscope", Phylum.MER_KIN );	// Fight a random Mer-Kin
		registerCard( 21, "XII - The Hanged Man", Phylum.ORC );		// Fight a random Orc
		registerCard( 27, "Suit Warehouse Discount Card", Phylum.PENGUIN );	// Fight a random Penguin
		registerCard( 23, "Pirate Birthday Card", Phylum.PIRATE );	// Fight a random Pirate
		registerCard( 22, "Plantable Greeting Card", Phylum.PLANT );	// Fight a random Plant
		registerCard( 18, "Slimer Trading Card", Phylum.SLIME );	// Fight a random Slime
		registerCard( 9, "XIII - Death", Phylum.UNDEAD );		// Fight a random Undead
		registerCard( 25, "Unstable Portal", Phylum.WEIRD );		// Fight a random Weird
	};

	private static EveryCard registerCard( int id, String name )
	{
		EveryCard card = new EveryCard( id, name );
		DeckOfEveryCardRequest.idToCard.put( id, card );
		DeckOfEveryCardRequest.canonicalNameToCard.put( StringUtilities.getCanonicalName( name ), card );
		return card;
	}

	private static void registerCard( int id, String name, Phylum phylum )
	{
		EveryCard card = DeckOfEveryCardRequest.registerCard( id, name );
		DeckOfEveryCardRequest.phylumToCard.put( phylum, card );
	}

	private static void registerCard( int id, String name, Stat stat )
	{
		EveryCard card = DeckOfEveryCardRequest.registerCard( id, name );
		DeckOfEveryCardRequest.statToCard.put( stat, card );
	}

	private static void registerCard( int id, String name, AdventureResult thing )
	{
		EveryCard card = DeckOfEveryCardRequest.registerCard( id, name );
		if ( thing.isStatusEffect() )
		{
			DeckOfEveryCardRequest.buffToCard.put( thing, card );
		}
	}

	private static String [] CANONICAL_CARDS_ARRAY;
	static
	{
		Set<String> keys = DeckOfEveryCardRequest.canonicalNameToCard.keySet();
		DeckOfEveryCardRequest.CANONICAL_CARDS_ARRAY = keys.toArray( new String[ keys.size() ] );
	};

	public static final List<String> getMatchingNames( final String substring )
	{
		return StringUtilities.getMatchingNames( DeckOfEveryCardRequest.CANONICAL_CARDS_ARRAY, substring );
	}

	public static EveryCard phylumToCard( Phylum phylum )
	{
		return DeckOfEveryCardRequest.phylumToCard.get( phylum );
	}

	public static EveryCard canonicalNameToCard( String name )
	{
		return DeckOfEveryCardRequest.canonicalNameToCard.get( name );
	}

	private EveryCard card;

	public DeckOfEveryCardRequest()
	{
		super( "choice.php" );
		this.addFormField( "whichchoice", "1085" );
		this.addFormField( "option", "1" );
		this.card = null;
	}

	public DeckOfEveryCardRequest( EveryCard card )
	{
		super( "choice.php" );
		this.addFormField( "whichchoice", "1086" );
		this.addFormField( "option", "1" );
		this.addFormField( "which", String.valueOf( card.id ) );
		this.card = card;
	}

	@Override
	protected boolean shouldFollowRedirect()
	{
		return true;
	}

	@Override
	public void run()
	{
		if ( GenericRequest.abortIfInFightOrChoice() )
		{
			return;
		}

		// If you can't get a deck into inventory, punt
		if ( !InventoryManager.retrieveItem( ItemPool.DECK_OF_EVERY_CARD, 1, true ) )
		{
			KoLmafia.updateDisplay( MafiaState.ERROR, "You don't have a Deck of Every Card available" );
			return;
		}

		// If you've used up your draws for the day, punt
		int drawsUsed = Preferences.getInteger( "_deckCardsDrawn" );
		int drawsNeeded = card == null ? 1 : 5;
		if ( drawsUsed + drawsNeeded > 15 )
		{
			KoLmafia.updateDisplay( MafiaState.ERROR, "You don't have enough draws left from the deck to do that today" );
			return;
		}

		GenericRequest useRequest = new GenericRequest( "inv_use.php" );
		useRequest.addFormField( "whichitem", String.valueOf( ItemPool.DECK_OF_EVERY_CARD ) );
		if ( this.card == null )
		{
			useRequest.addFormField( "which", "3" );
		}
		else
		{
			useRequest.addFormField( "cheat", "1" );
		}

		useRequest.run();

		String responseText = useRequest.responseText;

		// You're too beaten up. An accidental papercut would kill you at this point.
		if ( responseText.contains( "You're too beaten up" ) )
		{
			KoLmafia.updateDisplay( MafiaState.ERROR, "You are too beaten up to draw a card" );
			return;
		}

		// You've already drawn your day's allotment of cards from the Deck of Every Card.
		// Shouldn't happen, unless you've drawn cards outside of KoLmafia
		if ( responseText.contains( "You've already drawn your day's allotment of cards" ) )
		{
			KoLmafia.updateDisplay( MafiaState.ERROR, "You've already used all your draws for the day" );
			Preferences.setInteger( "_deckCardsDrawn", 15 );
			return;
		}

		// You're too drunk to draw.
		if ( responseText.contains( "You're too drunk to draw" ) )
		{
			KoLmafia.updateDisplay( MafiaState.ERROR, "You're too drunk to draw a card" );
			return;
		}

		// You don't have enough energy left to cheat today.
		if ( responseText.contains( "You don't have enough energy left to cheat today" ) )
		{
			KoLmafia.updateDisplay( MafiaState.ERROR, "You don't have enough draws left to cheat today" );
			return;
		}

		// If you have already cheated a particular card today, it will
		// not be available. Unfortunately, if you submit the request
		// to cheat that card again, KoL says "Huh?" - and counts it as
		// 5 draws. I submitted a bug report for this, but unless they
		// decide to fix it, we'd better make sure the card is
		// available

		if ( this.card != null && !responseText.contains( this.card.name ) )
		{
			KoLmafia.updateDisplay( MafiaState.ERROR, "That card is not currently available." );
			return;
		}

		super.run();

		responseText = this.responseText;

		// Are there any generic failures we should look for here?

		if ( this.card != null )
		{
			// <span class='guts'>Huh?</span>
			if ( responseText.contains( "<span class='guts'>Huh?</span>" ) )
			{
				KoLmafia.updateDisplay( MafiaState.ERROR, "You already drew that card today." );
				return;
			}

			// Otherwise, need to confirm the draw
			this.constructURLString( "choice.php?whichchoice=1085&option=1", true );
			super.run();
		}
	}

	// <div id="blurb">You draw a card: <b>X - The Wheel of Fortune</b><p>This card has a little wheel pinned to the center of it.</div>
	// <div>You draw a card: <b>Dark Ritual</b><p>This card looks like it contains a magic spell of some kind.</div>
	public static final Pattern DRAW_CARD_PATTERN = Pattern.compile( "<div id=\"blurb\">.*?You draw a card: <b>(.*?)</b><p>(.*?)</div>", Pattern.DOTALL );

	public static String parseCardEncounter( final String responseText )
	{
		Matcher matcher = DeckOfEveryCardRequest.DRAW_CARD_PATTERN.matcher( responseText );
		return  matcher.find() ? matcher.group( 1 ) : null;
	}

	// There's something written on the ground under the shovels: GGUGEWCCCI<center>
	public static final Pattern SPADE_CARD_PATTERN = Pattern.compile( "There's something written on the ground under the shovels: (.*?)<" );

	public static void postChoice1( final String responseText )
	{
		Matcher matcher = DeckOfEveryCardRequest.SPADE_CARD_PATTERN.matcher( responseText );
		if ( matcher.find() )
		{
			String message = "Spade letters: " + matcher.group( 1 );
			RequestLogger.printLine( message );
			RequestLogger.updateSessionLog( message );
		}
	}

	public static final Pattern URL_CARD_PATTERN = Pattern.compile( "which=(\\d+)" );
	public static EveryCard extractCardFromURL( final String urlString )
	{
		Matcher matcher = DeckOfEveryCardRequest.URL_CARD_PATTERN.matcher( urlString );
		return  matcher.find() ?
			DeckOfEveryCardRequest.idToCard.get( StringUtilities.parseInt( matcher.group( 1 ) ) ) :
			null;
	}

	public static boolean registerRequest( final String urlString )
	{
		if ( !urlString.startsWith( "choice.php" ) )
		{
			return false;
		}

		int choice = ChoiceManager.extractChoiceFromURL( urlString );

		if ( choice != 1085 && choice != 1086 )
		{
			return false;
		}

		EveryCard card = DeckOfEveryCardRequest.extractCardFromURL( urlString );
		if ( card == null )
		{
			// You are confirming the action for the card you drew
			return true;
		}

		String  message = "play " + card;
		RequestLogger.printLine( message );
		RequestLogger.updateSessionLog( message );

		return true;
	}

	public static class EveryCard
	{
		public int id;
		public String name;
		private String stringForm;

		public EveryCard( int id, String name )
		{
			this.id = id;
			this.name = name;
			this.stringForm = name + " (" + id + ")";
		}

		@Override
		public String toString()
		{
			return this.stringForm; 
		}
	}
}
